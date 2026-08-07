package com.carelink.clinical.infrastructure.web;

import com.carelink.clinical.domain.value.ServiceScope;
import com.carelink.identity.domain.Tenant;
import com.carelink.identity.domain.port.TenantRepository;
import com.carelink.identity.domain.value.TenantSlug;
import com.carelink.identity.infrastructure.security.AuthenticatedPrincipal;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Resuelve, para un request autenticado, el tenant (AC-06) y el alcance por servicio
 * (AC-06b) en los que ese caller puede operar. Existe como bean compartido en vez de
 * repetir {@code resolveTenantSlug} en cada controller clínico porque son exactamente
 * las dos reglas de aislamiento que TODO endpoint clínico tiene que aplicar igual —
 * cuatro copias del mismo método privado son cuatro lugares donde una puede quedar
 * desactualizada respecto de las otras.
 *
 * <p>Los dos métodos devuelven {@code Optional.empty()} cuando el caller no puede
 * operar, y el controller traduce eso a 403 — nunca a una respuesta que distinga
 * "no existe" de "no es tuyo" (mismo principio de AC-06).
 */
@Component
public class ClinicalRequestScope {

    private final TenantRepository tenantRepository;

    public ClinicalRequestScope(TenantRepository tenantRepository) {
        this.tenantRepository = tenantRepository;
    }

    /** El tenant del caller, derivado del JWT — nunca de un parámetro del request (AC-06). */
    public Optional<TenantSlug> tenantSlug(AuthenticatedPrincipal principal) {
        if (principal == null || principal.tenantId() == null) {
            return Optional.empty();
        }
        return tenantRepository.findById(principal.tenantId()).map(Tenant::slug);
    }

    /**
     * AC-06b — el alcance por servicio del caller.
     *
     * <p>Un rol NO exento (§4) sin {@code service_id} asignado devuelve
     * {@code Optional.empty()}, o sea 403: no ve nada. La alternativa —tratarlo como
     * irrestricto— haría que un usuario mal provisionado viera TODO el tenant, que es
     * exactamente el fallo que AC-06b existe para prevenir. Falla cerrado, no abierto.
     */
    public Optional<ServiceScope> serviceScope(AuthenticatedPrincipal principal) {
        if (principal == null) {
            return Optional.empty();
        }
        if (principal.isServiceScopeExempt()) {
            return Optional.of(ServiceScope.allServices());
        }
        String serviceId = principal.serviceId();
        if (serviceId == null || serviceId.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(ServiceScope.of(serviceId));
    }

    /**
     * §4 — {@code AUDITOR}: "Audit log only, no PHI read path". Hallazgo real de la
     * auditoría adversarial de portafolio (2026-08-07): {@code AUDITOR} está en
     * {@link AuthenticatedPrincipal#isServiceScopeExempt()} junto con
     * {@code TENANT_ADMIN} (ambos "ven todo el tenant sin filtrar por servicio"), sobre
     * el supuesto de que algún otro control impediría que llegara a leer PHI — pero
     * {@code PatientController}/{@code ClinicalEncounterController}/{@code
     * AdmissionController}/{@code HealthDiaryController}/{@code LabController} (los GET
     * que no son del Motor de Conocimiento ni de Farmacia, que sí traían su propia
     * lista de roles) no tenían NINGÚN chequeo de rol — solo tenant y servicio. Un
     * AUDITOR autenticado leía pacientes y encuentros clínicos completos: exactamente
     * lo que la línea de la tabla de §4 dice que no debería poder hacer.
     *
     * <p>El fix es deliberadamente ACOTADO al caso que el SRS deja inequívoco (AUDITOR,
     * explícitamente "no PHI read path") y no una re-definición completa de qué rol lee
     * qué recurso — esa matriz más fina (¿debería PHARMACIST leer un ClinicalEncounter
     * arbitrario del mismo servicio?) no está especificada con la misma claridad y
     * tocarla ahora arriesgaría romper flujos ya verificados en vivo sin una base clara
     * contra la cual corregir. Ver `tasks/todo.md` — queda nombrado como gap abierto,
     * no resuelto en silencio.
     */
    public boolean hasPhiReadAccess(AuthenticatedPrincipal principal) {
        return principal != null && !"AUDITOR".equals(principal.role());
    }
}
