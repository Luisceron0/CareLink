package com.carelink.clinical.infrastructure.web;

import com.carelink.clinical.application.usecase.GetPatientUseCase;
import com.carelink.clinical.application.usecase.RegisterPatientUseCase;
import com.carelink.clinical.domain.Patient;
import com.carelink.clinical.domain.value.BloodType;
import com.carelink.clinical.domain.value.DocumentType;
import com.carelink.clinical.domain.value.ServiceScope;
import com.carelink.clinical.domain.value.Sex;
import com.carelink.identity.infrastructure.security.AuthenticatedPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * FR-CLN-01. El tenant de cada operación se resuelve del {@link AuthenticatedPrincipal}
 * del request —nunca de un parámetro de URL o del body— así que no hay forma de que un
 * cliente pida "el paciente X del tenant Y": solo puede pedir "el paciente X de MI
 * tenant". Esto es lo que hace que AC-06 (lectura cross-tenant → 403) no dependa de un
 * chequeo que alguien pueda escribir mal — la posibilidad de pedir el tenant equivocado
 * no existe en la forma del endpoint.
 */
@RestController
@RequestMapping("/api/v1/patients")
public class PatientController {

    private final RegisterPatientUseCase registerPatientUseCase;
    private final GetPatientUseCase getPatientUseCase;
    private final ClinicalRequestScope requestScope;

    public PatientController(RegisterPatientUseCase registerPatientUseCase,
                              GetPatientUseCase getPatientUseCase,
                              ClinicalRequestScope requestScope) {
        this.registerPatientUseCase = registerPatientUseCase;
        this.getPatientUseCase = getPatientUseCase;
        this.requestScope = requestScope;
    }

    @PostMapping
    public ResponseEntity<?> register(@AuthenticationPrincipal AuthenticatedPrincipal principal,
                                       @RequestBody RegisterPatientRequest req) {
        Optional<com.carelink.identity.domain.value.TenantSlug> tenantSlug = requestScope.tenantSlug(principal);
        if (tenantSlug.isEmpty()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        Patient patient = registerPatientUseCase.execute(
                tenantSlug.get(),
                req.getFullName(),
                DocumentType.valueOf(req.getDocumentType()),
                req.getDocumentNumber(),
                LocalDate.parse(req.getDateOfBirth()),
                Sex.valueOf(req.getSex()),
                BloodType.valueOf(req.getBloodType()),
                req.getAllergies(),
                // AC-06b: el paciente queda estampado con el servicio de quien lo
                // registra. Un rol exento (TENANT_ADMIN) lo crea sin servicio.
                principal.serviceId());

        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(patient));
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> get(@AuthenticationPrincipal AuthenticatedPrincipal principal,
                                  @PathVariable UUID id) {
        // §4: AUDITOR no tiene PHI read path. Hallazgo de la auditoría de portafolio
        // (2026-08-07) — este chequeo no existía, ver el javadoc de hasPhiReadAccess.
        if (!requestScope.hasPhiReadAccess(principal)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        Optional<com.carelink.identity.domain.value.TenantSlug> tenantSlug = requestScope.tenantSlug(principal);
        if (tenantSlug.isEmpty()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        // AC-06: da lo mismo que `id` no exista en ningún lado o que exista pero en el
        // schema de otro tenant — getPatientUseCase nunca mira otro schema que el de
        // tenantSlug, así que ambos casos llegan acá como Optional.empty() y los dos se
        // responden igual. Que un intento cross-tenant no se distinga de un id
        // inexistente es la propiedad de seguridad, no un detalle de implementación:
        // lo contrario confirmaría a un atacante que el recurso existe en otro tenant.
        //
        // AC-06b agrega la misma propiedad DENTRO del tenant: si el paciente existe
        // pero pertenece a otro servicio, la consulta tampoco lo devuelve (el filtro
        // va en el WHERE, ver JdbcPatientRepository), así que se responde igual que un
        // id inexistente. Un PHYSICIAN de Urgencias no puede confirmar la existencia
        // de un paciente de Consulta Externa.
        Optional<ServiceScope> scope = requestScope.serviceScope(principal);
        if (scope.isEmpty()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        return getPatientUseCase.execute(tenantSlug.get(), id, scope.get())
                .map(patient -> ResponseEntity.ok(toResponse(patient)))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.FORBIDDEN).build());
    }

    private Map<String, Object> toResponse(Patient patient) {
        return Map.of(
                "id", patient.id().toString(),
                "fullName", patient.fullName(),
                "documentType", patient.documentId().type().name(),
                "documentNumber", patient.documentId().number(),
                "dateOfBirth", patient.dateOfBirth().toString(),
                "sex", patient.sex().name(),
                "bloodType", patient.bloodType().name(),
                "allergies", patient.allergies());
    }
}
