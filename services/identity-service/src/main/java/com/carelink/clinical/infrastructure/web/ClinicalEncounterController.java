package com.carelink.clinical.infrastructure.web;

import com.carelink.clinical.application.usecase.GetEncounterUseCase;
import com.carelink.clinical.application.usecase.RegisterEncounterUseCase;
import com.carelink.clinical.application.usecase.SignEncounterUseCase;
import com.carelink.clinical.application.usecase.UpdateEncounterUseCase;
import com.carelink.clinical.domain.ClinicalEncounter;
import com.carelink.clinical.domain.exception.EncounterAlreadySignedException;
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
 * FR-CLN-02, AC-08. Mismo principio de aislamiento por tenant que {@code PatientController}
 * — el tenant siempre sale de {@link AuthenticatedPrincipal}, nunca de un parámetro.
 *
 * <p>{@code physicianUserId}/{@code signedByUserId} son siempre {@code principal.userId()}
 * — quien crea o firma es siempre quien está autenticado, nunca un id que el cliente
 * pase por el body. Lo contrario permitiría que cualquiera cree o firme "en nombre de"
 * otro médico.
 */
@RestController
@RequestMapping("/api/v1/encounters")
public class ClinicalEncounterController {

    private static final String PHYSICIAN_ROLE = "PHYSICIAN";

    private final RegisterEncounterUseCase registerEncounterUseCase;
    private final UpdateEncounterUseCase updateEncounterUseCase;
    private final SignEncounterUseCase signEncounterUseCase;
    private final GetEncounterUseCase getEncounterUseCase;
    private final TenantRepository tenantRepository;

    public ClinicalEncounterController(RegisterEncounterUseCase registerEncounterUseCase,
                                        UpdateEncounterUseCase updateEncounterUseCase,
                                        SignEncounterUseCase signEncounterUseCase,
                                        GetEncounterUseCase getEncounterUseCase,
                                        TenantRepository tenantRepository) {
        this.registerEncounterUseCase = registerEncounterUseCase;
        this.updateEncounterUseCase = updateEncounterUseCase;
        this.signEncounterUseCase = signEncounterUseCase;
        this.getEncounterUseCase = getEncounterUseCase;
        this.tenantRepository = tenantRepository;
    }

    @PostMapping
    public ResponseEntity<?> register(@AuthenticationPrincipal AuthenticatedPrincipal principal,
                                       @RequestBody RegisterEncounterRequest req) {
        if (!PHYSICIAN_ROLE.equals(principal == null ? null : principal.role())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        Optional<TenantSlug> tenantSlug = resolveTenantSlug(principal);
        if (tenantSlug.isEmpty()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        ClinicalEncounter encounter = registerEncounterUseCase.execute(
                tenantSlug.get(), req.getPatientId(), principal.userId(),
                req.getChiefComplaint(), req.getExamFindings(), req.getDiagnosisCie10(),
                req.getTreatmentPlan(), req.getFollowUp());

        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(encounter));
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(@AuthenticationPrincipal AuthenticatedPrincipal principal,
                                     @PathVariable UUID id,
                                     @RequestBody RegisterEncounterRequest req) {
        if (!PHYSICIAN_ROLE.equals(principal == null ? null : principal.role())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        Optional<TenantSlug> tenantSlug = resolveTenantSlug(principal);
        if (tenantSlug.isEmpty()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        Optional<ClinicalEncounter> existing = getEncounterUseCase.execute(tenantSlug.get(), id);
        if (existing.isEmpty()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        ClinicalEncounter updated = new ClinicalEncounter(
                id, existing.get().patientId(), existing.get().physicianUserId(),
                req.getChiefComplaint(), req.getExamFindings(), req.getDiagnosisCie10(),
                req.getTreatmentPlan(), req.getFollowUp(),
                existing.get().createdAt(), existing.get().signedAt(), existing.get().signedByUserId());

        try {
            updateEncounterUseCase.execute(tenantSlug.get(), updated);
        } catch (EncounterAlreadySignedException e) {
            // AC-08: el trigger de la base rechazó la mutación porque ya estaba
            // firmado — 409, no un 500 que confundiría "no se pudo" con "está bien
            // así, no se puede cambiar" (que es exactamente lo que la inmutabilidad
            // de un encounter firmado significa).
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "El encuentro ya está firmado y es inmutable"));
        }

        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/sign")
    public ResponseEntity<?> sign(@AuthenticationPrincipal AuthenticatedPrincipal principal,
                                   @PathVariable UUID id) {
        if (!PHYSICIAN_ROLE.equals(principal == null ? null : principal.role())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        Optional<TenantSlug> tenantSlug = resolveTenantSlug(principal);
        if (tenantSlug.isEmpty()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        try {
            signEncounterUseCase.execute(tenantSlug.get(), id, principal.userId());
        } catch (EncounterAlreadySignedException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "El encuentro ya estaba firmado"));
        }

        return ResponseEntity.ok().build();
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> get(@AuthenticationPrincipal AuthenticatedPrincipal principal,
                                  @PathVariable UUID id) {
        Optional<TenantSlug> tenantSlug = resolveTenantSlug(principal);
        if (tenantSlug.isEmpty()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        return getEncounterUseCase.execute(tenantSlug.get(), id)
                .map(encounter -> ResponseEntity.ok(toResponse(encounter)))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.FORBIDDEN).build());
    }

    private Optional<TenantSlug> resolveTenantSlug(AuthenticatedPrincipal principal) {
        if (principal == null || principal.tenantId() == null) {
            return Optional.empty();
        }
        return tenantRepository.findById(principal.tenantId()).map(Tenant::slug);
    }

    private Map<String, Object> toResponse(ClinicalEncounter encounter) {
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("id", encounter.id().toString());
        body.put("patientId", encounter.patientId().toString());
        body.put("physicianUserId", encounter.physicianUserId().toString());
        body.put("chiefComplaint", encounter.chiefComplaint());
        body.put("examFindings", encounter.examFindings());
        body.put("diagnosisCie10", encounter.diagnosisCie10());
        body.put("treatmentPlan", encounter.treatmentPlan());
        body.put("followUp", encounter.followUp());
        body.put("signed", encounter.isSigned());
        body.put("signedAt", encounter.signedAt() == null ? null : encounter.signedAt().toString());
        return body;
    }
}
