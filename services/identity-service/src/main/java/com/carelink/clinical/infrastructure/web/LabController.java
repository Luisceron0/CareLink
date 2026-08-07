package com.carelink.clinical.infrastructure.web;

import com.carelink.clinical.application.usecase.GetLabOrderUseCase;
import com.carelink.clinical.application.usecase.OrderLabTestUseCase;
import com.carelink.clinical.application.usecase.RecordLabResultUseCase;
import com.carelink.clinical.domain.CriticalValueNotification;
import com.carelink.clinical.domain.LabOrder;
import com.carelink.clinical.domain.port.LabRepository;
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

/**
 * FR-CLN-11. §5.8: "{@code LAB_TECH} y {@code PHYSICIAN} gestionan órdenes" — ordenar es
 * de ambos; cargar el resultado es del laboratorio.
 */
@RestController
@RequestMapping("/api/v1/lab")
public class LabController {

    private static final Set<String> CAN_ORDER = Set.of("PHYSICIAN", "LAB_TECH");
    private static final String LAB_TECH_ROLE = "LAB_TECH";

    private final OrderLabTestUseCase orderUseCase;
    private final RecordLabResultUseCase recordResultUseCase;
    private final GetLabOrderUseCase getUseCase;
    private final LabRepository labRepository;
    private final ClinicalRequestScope requestScope;

    public LabController(OrderLabTestUseCase orderUseCase, RecordLabResultUseCase recordResultUseCase,
                          GetLabOrderUseCase getUseCase, LabRepository labRepository,
                          ClinicalRequestScope requestScope) {
        this.orderUseCase = orderUseCase;
        this.recordResultUseCase = recordResultUseCase;
        this.getUseCase = getUseCase;
        this.labRepository = labRepository;
        this.requestScope = requestScope;
    }

    @PostMapping("/orders")
    public ResponseEntity<?> order(@AuthenticationPrincipal AuthenticatedPrincipal principal,
                                    @RequestBody OrderLabTestRequest req) {
        if (principal == null || !CAN_ORDER.contains(principal.role())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        Optional<TenantSlug> tenantSlug = requestScope.tenantSlug(principal);
        if (tenantSlug.isEmpty()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        try {
            LabOrder order = orderUseCase.execute(tenantSlug.get(), req.getPatientId(), req.getEncounterId(),
                    principal.userId(), req.getTestCode(), req.getTestName(), principal.serviceId());
            return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(order));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/orders/{id}")
    public ResponseEntity<?> get(@AuthenticationPrincipal AuthenticatedPrincipal principal,
                                  @PathVariable UUID id) {
        // §4: AUDITOR no tiene PHI read path — hallazgo de la auditoría de portafolio
        // (2026-08-07), ver el javadoc de ClinicalRequestScope.hasPhiReadAccess.
        if (!requestScope.hasPhiReadAccess(principal)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        Optional<TenantSlug> tenantSlug = requestScope.tenantSlug(principal);
        Optional<ServiceScope> scope = requestScope.serviceScope(principal);
        if (tenantSlug.isEmpty() || scope.isEmpty()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return getUseCase.execute(tenantSlug.get(), id, scope.get())
                .map(o -> ResponseEntity.ok(toResponse(o)))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.FORBIDDEN).build());
    }

    @PostMapping("/orders/{id}/result")
    public ResponseEntity<?> recordResult(@AuthenticationPrincipal AuthenticatedPrincipal principal,
                                           @PathVariable UUID id,
                                           @RequestBody RecordLabResultRequest req) {
        if (!LAB_TECH_ROLE.equals(principal == null ? null : principal.role())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        Optional<TenantSlug> tenantSlug = requestScope.tenantSlug(principal);
        Optional<ServiceScope> scope = requestScope.serviceScope(principal);
        if (tenantSlug.isEmpty() || scope.isEmpty()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        boolean critical = Boolean.TRUE.equals(req.getCriticalValue());
        Optional<CriticalValueNotification> notification = recordResultUseCase.execute(
                tenantSlug.get(), id, req.getValue(), req.getUnits(), critical, principal.userId(), scope.get());

        // recordResult devuelve vacío tanto si el valor no era crítico como si el UPDATE
        // no afectó filas. Se distingue releyendo: si la orden ahora tiene resultado, la
        // carga funcionó.
        Optional<LabOrder> after = getUseCase.execute(tenantSlug.get(), id, scope.get());
        if (after.isEmpty() || !after.get().hasResult()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        Map<String, Object> body = new HashMap<>();
        body.put("criticalValue", critical);
        body.put("notificationCreated", notification.isPresent());
        notification.ifPresent(nx -> body.put("notifiedUserId", nx.notifyUserId().toString()));
        return ResponseEntity.ok(body);
    }

    /** FR-CLN-11 — la "notificación" de este milestone: una fila que el médico consulta (§16.4). */
    @GetMapping("/notifications")
    public ResponseEntity<?> pendingNotifications(@AuthenticationPrincipal AuthenticatedPrincipal principal) {
        Optional<TenantSlug> tenantSlug = requestScope.tenantSlug(principal);
        if (principal == null || tenantSlug.isEmpty()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        // Sin ServiceScope a propósito: el filtro es notify_user_id, más estrecho que el
        // servicio. Un médico ve SUS notificaciones, no las de su departamento.
        List<Map<String, Object>> rows = new ArrayList<>();
        for (CriticalValueNotification nx : labRepository.findPendingNotifications(tenantSlug.get(), principal.userId())) {
            Map<String, Object> m = new HashMap<>();
            m.put("id", nx.id().toString());
            m.put("labOrderId", nx.labOrderId().toString());
            m.put("patientId", nx.patientId().toString());
            m.put("createdAt", nx.createdAt().toString());
            rows.add(m);
        }
        return ResponseEntity.ok(Map.of("pending", rows));
    }

    @PostMapping("/notifications/{id}/acknowledge")
    public ResponseEntity<?> acknowledge(@AuthenticationPrincipal AuthenticatedPrincipal principal,
                                          @PathVariable UUID id) {
        Optional<TenantSlug> tenantSlug = requestScope.tenantSlug(principal);
        if (principal == null || tenantSlug.isEmpty()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        boolean ok = labRepository.acknowledgeNotification(tenantSlug.get(), id, principal.userId());
        return ok ? ResponseEntity.ok().build() : ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }

    private Map<String, Object> toResponse(LabOrder o) {
        Map<String, Object> body = new HashMap<>();
        body.put("id", o.id().toString());
        body.put("patientId", o.patientId().toString());
        body.put("clinicalEncounterId", o.clinicalEncounterId().toString());
        body.put("testCode", o.testCode());
        body.put("testName", o.testName());
        body.put("resultValue", o.resultValue());
        body.put("resultUnits", o.resultUnits());
        body.put("criticalValue", o.criticalValue());
        body.put("hasResult", o.hasResult());
        return body;
    }
}
