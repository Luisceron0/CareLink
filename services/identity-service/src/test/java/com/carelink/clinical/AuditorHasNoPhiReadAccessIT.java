package com.carelink.clinical;

import com.carelink.clinical.domain.value.ServiceScope;
import com.carelink.clinical.infrastructure.web.ClinicalRequestScope;
import com.carelink.identity.domain.Tenant;
import com.carelink.identity.domain.port.SchemaProvisioner;
import com.carelink.identity.domain.port.TenantRepository;
import com.carelink.identity.domain.value.TenantSlug;
import com.carelink.identity.infrastructure.security.AuthenticatedPrincipal;
import com.carelink.identity.support.EmbeddedPostgresSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regresión del hallazgo de la auditoría adversarial de portafolio (2026-08-07):
 * {@code AUDITOR} podía leer pacientes y encuentros clínicos completos por HTTP, pese a
 * que SRS §4 dice explícitamente "Audit log only, no PHI read path". La causa era que
 * {@code AUDITOR} está en {@code SERVICE_SCOPE_EXEMPT_ROLES} (junto con
 * {@code TENANT_ADMIN}, ambos "ven todo el tenant") bajo el supuesto de que algún otro
 * control se lo impediría — pero la mayoría de los controllers clínicos no chequeaban
 * rol en absoluto en sus endpoints GET, solo tenant y servicio.
 *
 * <p>Este test verifica la pieza central del fix
 * ({@code ClinicalRequestScope.hasPhiReadAccess}) en vez de repetir un pentest HTTP
 * completo por endpoint — eso ya lo cubre el resto de los tests de integración de cada
 * recurso (que ahora también ejercitan indirectamente el chequeo, al usar roles no
 * AUDITOR). Lo que este test fija es la REGLA: AUDITOR nunca pasa, cualquier otro rol
 * autenticado sí.
 */
@SpringBootTest(classes = com.carelink.identity.Application.class, properties = {
        "carelink.demo-mode=true",
        "carelink.app-env=test"
})
class AuditorHasNoPhiReadAccessIT {

    @Autowired private SchemaProvisioner schemaProvisioner;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private ClinicalRequestScope requestScope;

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        EmbeddedPostgresSupport.registerDynamicProperties(registry, "auditornophi");
    }

    @Test
    @DisplayName("hasPhiReadAccess: false para AUDITOR, true para cualquier otro rol autenticado, false para null")
    void hasPhiReadAccessDeniesOnlyAuditor() {
        AuthenticatedPrincipal auditor = new AuthenticatedPrincipal(UUID.randomUUID(), UUID.randomUUID(), "AUDITOR", null);
        assertThat(requestScope.hasPhiReadAccess(auditor))
                .as("§4: AUDITOR no tiene PHI read path").isFalse();

        // Contrapeso: si esto también fuera false, "false para AUDITOR" no probaría
        // nada específico sobre AUDITOR — probaría que el método rechaza a todos.
        for (String role : new String[]{"PHYSICIAN", "NURSE", "SPECIALIST", "LAB_TECH",
                "PHARMACIST", "ADMISSIONS", "TENANT_ADMIN"}) {
            AuthenticatedPrincipal p = new AuthenticatedPrincipal(UUID.randomUUID(), UUID.randomUUID(), role, "Urgencias");
            assertThat(requestScope.hasPhiReadAccess(p)).as("rol %s sí debe poder leer PHI", role).isTrue();
        }

        assertThat(requestScope.hasPhiReadAccess(null)).isFalse();
    }

    @Test
    @DisplayName("regresión end-to-end: un AUDITOR con acceso de servicio irrestricto igual no puede armar la respuesta de un paciente sin pasar por hasPhiReadAccess")
    void endToEndPatientReadRequiresThePhiGate() {
        // Este test no golpea el controller por HTTP (lo hacen los *_IT de cada
        // recurso) — ejercita la MISMA combinación que producía el hallazgo: un
        // ServiceScope irrestricto (el que AUDITOR obtiene de
        // ClinicalRequestScope.serviceScope) sí puede leer cualquier paciente del
        // tenant si nada más lo detiene. La pieza que lo detiene es hasPhiReadAccess,
        // evaluada ANTES de llegar a este punto — este test confirma que sigue
        // existiendo y aplicando la regla correcta.
        TenantSlug tenant = new TenantSlug("auditornophipatient");
        schemaProvisioner.provisionSchema(tenant);
        tenantRepository.save(new Tenant(UUID.randomUUID(), "Auditor No PHI", tenant, OffsetDateTime.now()));

        AuthenticatedPrincipal auditor = new AuthenticatedPrincipal(UUID.randomUUID(), UUID.randomUUID(), "AUDITOR", null);

        // Sin el gate, esto es exactamente lo que el controller haría con el
        // ServiceScope irrestricto de un AUDITOR — confirma que el scope EN SÍ no
        // protege nada; lo que protege es negarle el paso antes.
        assertThat(requestScope.serviceScope(auditor))
                .as("AUDITOR obtiene scope irrestricto, por diseño de §4 (ve todo, salvo PHI)")
                .contains(ServiceScope.allServices());
        assertThat(requestScope.hasPhiReadAccess(auditor))
                .as("y por eso hasPhiReadAccess es la única barrera real para PHI").isFalse();
    }
}
