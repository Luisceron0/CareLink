package com.carelink.clinical.infrastructure.web;

import com.carelink.clinical.application.usecase.CheckPrescriptionConflictsUseCase;
import com.carelink.clinical.application.usecase.DispenseMedicationUseCase;
import com.carelink.clinical.application.usecase.GetAdherenceUseCase;
import com.carelink.clinical.domain.AdherenceIndex;
import com.carelink.clinical.domain.PrescriptionConflict;
import com.carelink.clinical.domain.value.ServiceScope;
import com.carelink.identity.domain.value.TenantSlug;
import com.carelink.identity.infrastructure.security.AuthenticatedPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** FR-CLN-12. Dispensar es del {@code PHARMACIST} (§4); adherencia y conflictos los consulta el equipo clínico. */
@RestController
@RequestMapping("/api/v1/pharmacy")
public class PharmacyController {

    private static final String PHARMACIST_ROLE = "PHARMACIST";
    private static final Set<String> CLINICAL_READERS =
            Set.of("PHYSICIAN", "NURSE", "SPECIALIST", "PHARMACIST", "TENANT_ADMIN");

    private final DispenseMedicationUseCase dispenseUseCase;
    private final GetAdherenceUseCase adherenceUseCase;
    private final CheckPrescriptionConflictsUseCase conflictsUseCase;
    private final ClinicalRequestScope requestScope;

    public PharmacyController(DispenseMedicationUseCase dispenseUseCase, GetAdherenceUseCase adherenceUseCase,
                               CheckPrescriptionConflictsUseCase conflictsUseCase,
                               ClinicalRequestScope requestScope) {
        this.dispenseUseCase = dispenseUseCase;
        this.adherenceUseCase = adherenceUseCase;
        this.conflictsUseCase = conflictsUseCase;
        this.requestScope = requestScope;
    }

    @PostMapping("/dispensations")
    public ResponseEntity<?> dispense(@AuthenticationPrincipal AuthenticatedPrincipal principal,
                                       @RequestBody DispenseRequest req) {
        if (!PHARMACIST_ROLE.equals(principal == null ? null : principal.role())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        Optional<TenantSlug> tenantSlug = requestScope.tenantSlug(principal);
        Optional<ServiceScope> scope = requestScope.serviceScope(principal);
        if (tenantSlug.isEmpty() || scope.isEmpty()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        if (req.getDosesDispensed() == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "dosesDispensed es obligatorio"));
        }

        boolean ok;
        try {
            ok = dispenseUseCase.execute(tenantSlug.get(), req.getPrescriptionId(), req.getPatientId(),
                    principal.userId(), req.getDosesDispensed(), principal.serviceId(), scope.get());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        }
        return ok ? ResponseEntity.status(HttpStatus.CREATED).build()
                : ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }

    @GetMapping("/prescriptions/{id}/adherence")
    public ResponseEntity<?> adherence(@AuthenticationPrincipal AuthenticatedPrincipal principal,
                                        @PathVariable UUID id) {
        if (principal == null || !CLINICAL_READERS.contains(principal.role())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        Optional<TenantSlug> tenantSlug = requestScope.tenantSlug(principal);
        Optional<ServiceScope> scope = requestScope.serviceScope(principal);
        if (tenantSlug.isEmpty() || scope.isEmpty()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        return adherenceUseCase.execute(tenantSlug.get(), id, scope.get())
                .map(this::adherenceBody)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.FORBIDDEN).build());
    }

    /**
     * FR-CLN-12 — los conflictos ADVIERTEN, nunca bloquean. Este endpoint devuelve 200
     * con la lista incluso cuando encuentra conflictos: un 409 sería exactamente el
     * bloqueo que el requisito prohíbe, y el médico que consulta esto está evaluando,
     * no pidiendo permiso.
     */
    @GetMapping("/conflicts")
    public ResponseEntity<?> conflicts(@AuthenticationPrincipal AuthenticatedPrincipal principal,
                                        @RequestParam UUID patientId,
                                        @RequestParam String medication,
                                        @RequestParam(required = false) String medicationClass) {
        if (principal == null || !CLINICAL_READERS.contains(principal.role())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        Optional<TenantSlug> tenantSlug = requestScope.tenantSlug(principal);
        Optional<ServiceScope> scope = requestScope.serviceScope(principal);
        if (tenantSlug.isEmpty() || scope.isEmpty()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        List<PrescriptionConflict> conflicts = conflictsUseCase.execute(
                tenantSlug.get(), patientId, medication, medicationClass, scope.get());

        List<Map<String, String>> rows = new ArrayList<>();
        for (PrescriptionConflict c : conflicts) {
            rows.add(Map.of("type", c.type().name(), "detail", c.detail()));
        }

        Map<String, Object> body = new HashMap<>();
        body.put("conflicts", rows);
        body.put("blocking", false);
        body.put("note", "Advertencia clínica, no bloqueo (FR-CLN-12): la decisión es del prescriptor.");
        return ResponseEntity.ok(body);
    }

    private Map<String, Object> adherenceBody(AdherenceIndex a) {
        Map<String, Object> body = new HashMap<>();
        body.put("prescriptionId", a.prescriptionId().toString());
        body.put("prescribedDoses", a.prescribedDoses());
        body.put("dispensedDoses", a.dispensedDoses());
        body.put("ratio", a.ratio());
        body.put("calculable", a.isCalculable());
        if (!a.isCalculable()) {
            // Un ratio nulo no es 0%: "no se registró el total de dosis" y "el paciente
            // no tomó nada" son afirmaciones clínicas distintas.
            body.put("note", "Sin total de dosis registrado en la prescripción: la adherencia no es calculable.");
        }
        return body;
    }
}
