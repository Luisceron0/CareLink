package com.carelink.clinical.infrastructure.web;

import com.carelink.clinical.application.usecase.GetEncounterUseCase;
import com.carelink.clinical.application.usecase.RegisterEncounterUseCase;
import com.carelink.clinical.application.usecase.SignEncounterUseCase;
import com.carelink.clinical.application.usecase.UpdateEncounterUseCase;
import com.carelink.clinical.domain.ClinicalEncounter;
import com.carelink.clinical.domain.exception.EncounterAlreadySignedException;
import com.carelink.clinical.domain.value.ServiceScope;
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
    private final ClinicalRequestScope requestScope;

    public ClinicalEncounterController(RegisterEncounterUseCase registerEncounterUseCase,
                                        UpdateEncounterUseCase updateEncounterUseCase,
                                        SignEncounterUseCase signEncounterUseCase,
                                        GetEncounterUseCase getEncounterUseCase,
                                        ClinicalRequestScope requestScope) {
        this.registerEncounterUseCase = registerEncounterUseCase;
        this.updateEncounterUseCase = updateEncounterUseCase;
        this.signEncounterUseCase = signEncounterUseCase;
        this.getEncounterUseCase = getEncounterUseCase;
        this.requestScope = requestScope;
    }

    @PostMapping
    public ResponseEntity<?> register(@AuthenticationPrincipal AuthenticatedPrincipal principal,
                                       @RequestBody RegisterEncounterRequest req) {
        if (!PHYSICIAN_ROLE.equals(principal == null ? null : principal.role())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        Optional<TenantSlug> tenantSlug = requestScope.tenantSlug(principal);
        if (tenantSlug.isEmpty()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        ClinicalEncounter encounter = registerEncounterUseCase.execute(
                tenantSlug.get(), req.getPatientId(), principal.userId(),
                req.getChiefComplaint(), req.getExamFindings(), req.getDiagnosisCie10(),
                req.getTreatmentPlan(), req.getFollowUp(),
                // AC-06b: el encounter queda estampado con el servicio del médico.
                principal.serviceId());

        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(encounter));
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(@AuthenticationPrincipal AuthenticatedPrincipal principal,
                                     @PathVariable UUID id,
                                     @RequestBody RegisterEncounterRequest req) {
        if (!PHYSICIAN_ROLE.equals(principal == null ? null : principal.role())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        Optional<TenantSlug> tenantSlug = requestScope.tenantSlug(principal);
        if (tenantSlug.isEmpty()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        Optional<ServiceScope> scope = requestScope.serviceScope(principal);
        if (scope.isEmpty()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        // AC-06b: la lectura previa ya va filtrada por servicio, así que un encounter
        // de otro servicio no se puede ni leer ni —por lo tanto— editar. El chequeo no
        // se repite en el UPDATE porque no hay forma de llegar hasta él sin haber
        // pasado por esta lectura.
        Optional<ClinicalEncounter> existing = getEncounterUseCase.execute(tenantSlug.get(), id, scope.get());
        if (existing.isEmpty()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        ClinicalEncounter updated = new ClinicalEncounter(
                id, existing.get().patientId(), existing.get().physicianUserId(),
                req.getChiefComplaint(), req.getExamFindings(), req.getDiagnosisCie10(),
                req.getTreatmentPlan(), req.getFollowUp(),
                existing.get().serviceId(), existing.get().createdAt(), existing.get().signedAt(),
                existing.get().signedByUserId());

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
        Optional<TenantSlug> tenantSlug = requestScope.tenantSlug(principal);
        if (tenantSlug.isEmpty()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        Optional<ServiceScope> signScope = requestScope.serviceScope(principal);
        if (signScope.isEmpty()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        // AC-06b: firmar es una mutación, así que pasa por la misma lectura filtrada
        // por servicio antes de tocar nada — un médico de otro servicio no puede
        // firmar este encounter, y tampoco distingue "no existe" de "no es tuyo".
        if (getEncounterUseCase.execute(tenantSlug.get(), id, signScope.get()).isEmpty()) {
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
        // §4: AUDITOR no tiene PHI read path — hallazgo de la auditoría de portafolio
        // (2026-08-07), ver el javadoc de ClinicalRequestScope.hasPhiReadAccess.
        if (!requestScope.hasPhiReadAccess(principal)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        Optional<TenantSlug> tenantSlug = requestScope.tenantSlug(principal);
        if (tenantSlug.isEmpty()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        Optional<ServiceScope> readScope = requestScope.serviceScope(principal);
        if (readScope.isEmpty()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        return getEncounterUseCase.execute(tenantSlug.get(), id, readScope.get())
                .map(encounter -> ResponseEntity.ok(toResponse(encounter)))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.FORBIDDEN).build());
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
