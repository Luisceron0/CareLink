package com.carelink.clinical.infrastructure.web;

import com.carelink.clinical.application.usecase.GetAdmissionUseCase;
import com.carelink.clinical.application.usecase.LinkEncounterToAdmissionUseCase;
import com.carelink.clinical.application.usecase.RegisterAdmissionUseCase;
import com.carelink.clinical.domain.Admission;
import com.carelink.clinical.domain.value.AdmissionType;
import com.carelink.identity.domain.Tenant;
import com.carelink.identity.domain.port.TenantRepository;
import com.carelink.identity.domain.value.TenantSlug;
import com.carelink.identity.infrastructure.security.AuthenticatedPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * FR-CLN-03. Mismo patrón de aislamiento por tenant que el resto de {@code clinical} —
 * el tenant siempre sale de {@link AuthenticatedPrincipal}, nunca de un parámetro.
 *
 * <p>Registrar una admisión es {@code ADMISSIONS} (§4: "Patient registration, admission,
 * triage intake"). Vincular el encounter que se abrió durante esa admisión es
 * {@code PHYSICIAN} — es quien abre el encounter y sabe a qué admisión corresponde, no
 * quien hizo el ingreso originalmente.
 */
@RestController
@RequestMapping("/api/v1/admissions")
public class AdmissionController {

    private static final String ADMISSIONS_ROLE = "ADMISSIONS";
    private static final String PHYSICIAN_ROLE = "PHYSICIAN";

    private final RegisterAdmissionUseCase registerAdmissionUseCase;
    private final LinkEncounterToAdmissionUseCase linkEncounterToAdmissionUseCase;
    private final GetAdmissionUseCase getAdmissionUseCase;
    private final TenantRepository tenantRepository;

    public AdmissionController(RegisterAdmissionUseCase registerAdmissionUseCase,
                                LinkEncounterToAdmissionUseCase linkEncounterToAdmissionUseCase,
                                GetAdmissionUseCase getAdmissionUseCase,
                                TenantRepository tenantRepository) {
        this.registerAdmissionUseCase = registerAdmissionUseCase;
        this.linkEncounterToAdmissionUseCase = linkEncounterToAdmissionUseCase;
        this.getAdmissionUseCase = getAdmissionUseCase;
        this.tenantRepository = tenantRepository;
    }

    @PostMapping
    public ResponseEntity<?> register(@AuthenticationPrincipal AuthenticatedPrincipal principal,
                                       @RequestBody RegisterAdmissionRequest req) {
        if (!ADMISSIONS_ROLE.equals(principal == null ? null : principal.role())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        Optional<TenantSlug> tenantSlug = resolveTenantSlug(principal);
        if (tenantSlug.isEmpty()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        AdmissionType admissionType;
        try {
            admissionType = AdmissionType.valueOf(req.getAdmissionType());
        } catch (IllegalArgumentException | NullPointerException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "admissionType inválido: " + req.getAdmissionType()));
        }

        try {
            Admission admission = registerAdmissionUseCase.execute(
                    tenantSlug.get(), req.getPatientId(), admissionType, req.getTriagePriority(), principal.userId());
            return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(admission));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/{id}/link-encounter")
    public ResponseEntity<?> linkEncounter(@AuthenticationPrincipal AuthenticatedPrincipal principal,
                                            @PathVariable UUID id,
                                            @RequestBody LinkEncounterRequest req) {
        if (!PHYSICIAN_ROLE.equals(principal == null ? null : principal.role())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        Optional<TenantSlug> tenantSlug = resolveTenantSlug(principal);
        if (tenantSlug.isEmpty()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        boolean linked = linkEncounterToAdmissionUseCase.execute(tenantSlug.get(), id, req.getEncounterId());
        return linked ? ResponseEntity.ok().build() : ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> get(@AuthenticationPrincipal AuthenticatedPrincipal principal,
                                  @PathVariable UUID id) {
        Optional<TenantSlug> tenantSlug = resolveTenantSlug(principal);
        if (tenantSlug.isEmpty()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        return getAdmissionUseCase.execute(tenantSlug.get(), id)
                .map(admission -> ResponseEntity.ok(toResponse(admission)))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.FORBIDDEN).build());
    }

    private Optional<TenantSlug> resolveTenantSlug(AuthenticatedPrincipal principal) {
        if (principal == null || principal.tenantId() == null) {
            return Optional.empty();
        }
        return tenantRepository.findById(principal.tenantId()).map(Tenant::slug);
    }

    private Map<String, Object> toResponse(Admission admission) {
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("id", admission.id().toString());
        body.put("patientId", admission.patientId().toString());
        body.put("admissionType", admission.admissionType().name());
        body.put("triagePriority", admission.triagePriority() == null ? null : admission.triagePriority().value());
        body.put("admittedByUserId", admission.admittedByUserId().toString());
        body.put("admittedAt", admission.admittedAt().toString());
        body.put("clinicalEncounterId", admission.clinicalEncounterId() == null ? null : admission.clinicalEncounterId().toString());
        return body;
    }
}
