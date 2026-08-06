package com.carelink.clinical.infrastructure.web;

import com.carelink.clinical.application.usecase.GetDiaryEntryUseCase;
import com.carelink.clinical.application.usecase.RecordDiaryEntryUseCase;
import com.carelink.clinical.application.usecase.RecordInterventionOutcomeUseCase;
import com.carelink.clinical.domain.HealthDiaryEntry;
import com.carelink.clinical.domain.HealthIntervention;
import com.carelink.clinical.domain.VitalSigns;
import com.carelink.clinical.domain.value.ServiceScope;
import com.carelink.clinical.domain.value.Shift;
import com.carelink.identity.domain.value.TenantSlug;
import com.carelink.identity.infrastructure.security.AuthenticatedPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * FR-CLN-04, FR-CLN-05. Escribir en el diario es {@code NURSE} (§4: "Writes health diary
 * entries, vitals, interventions, medication administration"). Leerlo lo puede hacer
 * cualquier rol clínico dentro de su servicio — el filtro por servicio (AC-06b) hace el
 * trabajo de acotar, no una lista blanca de roles más restrictiva de lo que §4 pide.
 */
@RestController
@RequestMapping("/api/v1/diary")
public class HealthDiaryController {

    private static final String NURSE_ROLE = "NURSE";

    private final RecordDiaryEntryUseCase recordDiaryEntryUseCase;
    private final GetDiaryEntryUseCase getDiaryEntryUseCase;
    private final RecordInterventionOutcomeUseCase recordInterventionOutcomeUseCase;
    private final ClinicalRequestScope requestScope;

    public HealthDiaryController(RecordDiaryEntryUseCase recordDiaryEntryUseCase,
                                  GetDiaryEntryUseCase getDiaryEntryUseCase,
                                  RecordInterventionOutcomeUseCase recordInterventionOutcomeUseCase,
                                  ClinicalRequestScope requestScope) {
        this.recordDiaryEntryUseCase = recordDiaryEntryUseCase;
        this.getDiaryEntryUseCase = getDiaryEntryUseCase;
        this.recordInterventionOutcomeUseCase = recordInterventionOutcomeUseCase;
        this.requestScope = requestScope;
    }

    @PostMapping("/entries")
    public ResponseEntity<?> record(@AuthenticationPrincipal AuthenticatedPrincipal principal,
                                     @RequestBody RecordDiaryEntryRequest req) {
        if (!NURSE_ROLE.equals(principal == null ? null : principal.role())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        Optional<TenantSlug> tenantSlug = requestScope.tenantSlug(principal);
        if (tenantSlug.isEmpty()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        Shift shift;
        LocalDate entryDate;
        try {
            shift = Shift.valueOf(req.getShift());
            entryDate = LocalDate.parse(req.getEntryDate());
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "shift o entryDate inválidos"));
        }

        List<VitalSigns> vitals = new ArrayList<>();
        if (req.getVitalSigns() != null) {
            for (var v : req.getVitalSigns()) {
                // ids y timestamps los pone el caso de uso, no el cliente.
                vitals.add(new VitalSigns(null, null, v.getSystolicMmHg(), v.getDiastolicMmHg(),
                        v.getHeartRateBpm(), v.getRespiratoryRate(), v.getTemperatureCelsius(),
                        v.getOxygenSaturation(), null));
            }
        }

        List<HealthIntervention> interventions = new ArrayList<>();
        if (req.getInterventions() != null) {
            for (var i : req.getInterventions()) {
                try {
                    interventions.add(new HealthIntervention(null, null, req.getPatientId(), i.getNandaCode(),
                            i.getNicCode(), i.getDiagnosisCie10(), i.getDescription(), null, null, null));
                } catch (IllegalArgumentException e) {
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
                }
            }
        }

        try {
            HealthDiaryEntry entry = recordDiaryEntryUseCase.execute(
                    tenantSlug.get(), req.getPatientId(), principal.userId(), entryDate, shift,
                    req.getObservations(), vitals, interventions, principal.serviceId());
            return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(entry));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/entries/{id}")
    public ResponseEntity<?> get(@AuthenticationPrincipal AuthenticatedPrincipal principal,
                                  @PathVariable UUID id) {
        Optional<TenantSlug> tenantSlug = requestScope.tenantSlug(principal);
        Optional<ServiceScope> scope = requestScope.serviceScope(principal);
        if (tenantSlug.isEmpty() || scope.isEmpty()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        return getDiaryEntryUseCase.execute(tenantSlug.get(), id, scope.get())
                .map(entry -> ResponseEntity.ok(toResponse(entry)))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.FORBIDDEN).build());
    }

    /** FR-CLN-05 — registrar el resultado NOC de una intervención ya ejecutada. */
    @PostMapping("/interventions/{id}/outcome")
    public ResponseEntity<?> recordOutcome(@AuthenticationPrincipal AuthenticatedPrincipal principal,
                                            @PathVariable UUID id,
                                            @RequestBody RecordOutcomeRequest req) {
        if (!NURSE_ROLE.equals(principal == null ? null : principal.role())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        Optional<TenantSlug> tenantSlug = requestScope.tenantSlug(principal);
        Optional<ServiceScope> scope = requestScope.serviceScope(principal);
        if (tenantSlug.isEmpty() || scope.isEmpty()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        if (req.getEffectiveness() == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "effectiveness es obligatorio"));
        }

        boolean recorded;
        try {
            recorded = recordInterventionOutcomeUseCase.execute(
                    tenantSlug.get(), id, req.getNocCode(), req.getEffectiveness(), req.getNotes(), scope.get());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        }

        // 0 filas cubre por igual "no existe", "no es de tu servicio" y "ya tenía
        // resultado" — 409 sería más informativo pero distinguiría los tres casos, y los
        // dos primeros son justamente los que AC-06/AC-06b piden no distinguir.
        return recorded ? ResponseEntity.ok().build() : ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }

    private Map<String, Object> toResponse(HealthDiaryEntry entry) {
        Map<String, Object> body = new HashMap<>();
        body.put("id", entry.id().toString());
        body.put("patientId", entry.patientId().toString());
        body.put("nurseUserId", entry.nurseUserId().toString());
        body.put("entryDate", entry.entryDate().toString());
        body.put("shift", entry.shift().name());
        body.put("observations", entry.observations());

        List<Map<String, Object>> vitals = new ArrayList<>();
        for (VitalSigns v : entry.vitalSigns()) {
            Map<String, Object> m = new HashMap<>();
            m.put("id", v.id().toString());
            m.put("systolicMmHg", v.systolicMmHg());
            m.put("diastolicMmHg", v.diastolicMmHg());
            m.put("heartRateBpm", v.heartRateBpm());
            m.put("respiratoryRate", v.respiratoryRate());
            m.put("temperatureCelsius", v.temperatureCelsius());
            m.put("oxygenSaturation", v.oxygenSaturation());
            vitals.add(m);
        }
        body.put("vitalSigns", vitals);

        List<Map<String, Object>> interventions = new ArrayList<>();
        for (HealthIntervention i : entry.interventions()) {
            Map<String, Object> m = new HashMap<>();
            m.put("id", i.id().toString());
            m.put("nandaCode", i.nandaCode());
            m.put("nicCode", i.nicCode());
            m.put("diagnosisCie10", i.diagnosisCie10());
            m.put("description", i.description());
            if (i.hasOutcome()) {
                m.put("nocCode", i.outcome().nocCode());
                m.put("effectiveness", i.outcome().effectiveness());
            }
            interventions.add(m);
        }
        body.put("interventions", interventions);
        return body;
    }
}
