package com.carelink.clinical.infrastructure.web;

import com.carelink.clinical.application.usecase.GetPatientUseCase;
import com.carelink.clinical.application.usecase.RegisterPatientUseCase;
import com.carelink.clinical.domain.Patient;
import com.carelink.clinical.domain.value.BloodType;
import com.carelink.clinical.domain.value.DocumentType;
import com.carelink.clinical.domain.value.Sex;
import com.carelink.identity.domain.Tenant;
import com.carelink.identity.domain.port.TenantRepository;
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
    private final TenantRepository tenantRepository;

    public PatientController(RegisterPatientUseCase registerPatientUseCase,
                              GetPatientUseCase getPatientUseCase,
                              TenantRepository tenantRepository) {
        this.registerPatientUseCase = registerPatientUseCase;
        this.getPatientUseCase = getPatientUseCase;
        this.tenantRepository = tenantRepository;
    }

    @PostMapping
    public ResponseEntity<?> register(@AuthenticationPrincipal AuthenticatedPrincipal principal,
                                       @RequestBody RegisterPatientRequest req) {
        Optional<com.carelink.identity.domain.value.TenantSlug> tenantSlug = resolveTenantSlug(principal);
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
                req.getAllergies());

        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(patient));
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> get(@AuthenticationPrincipal AuthenticatedPrincipal principal,
                                  @PathVariable UUID id) {
        Optional<com.carelink.identity.domain.value.TenantSlug> tenantSlug = resolveTenantSlug(principal);
        if (tenantSlug.isEmpty()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        // AC-06: da lo mismo que `id` no exista en ningún lado o que exista pero en el
        // schema de otro tenant — getPatientUseCase nunca mira otro schema que el de
        // tenantSlug, así que ambos casos llegan acá como Optional.empty() y los dos se
        // responden igual. Que un intento cross-tenant no se distinga de un id
        // inexistente es la propiedad de seguridad, no un detalle de implementación:
        // lo contrario confirmaría a un atacante que el recurso existe en otro tenant.
        return getPatientUseCase.execute(tenantSlug.get(), id)
                .map(patient -> ResponseEntity.ok(toResponse(patient)))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.FORBIDDEN).build());
    }

    private Optional<com.carelink.identity.domain.value.TenantSlug> resolveTenantSlug(AuthenticatedPrincipal principal) {
        if (principal == null || principal.tenantId() == null) {
            return Optional.empty();
        }
        return tenantRepository.findById(principal.tenantId()).map(Tenant::slug);
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
