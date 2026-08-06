package com.carelink.clinical.infrastructure.web;

import com.carelink.clinical.application.usecase.SearchKnowledgeUseCase;
import com.carelink.clinical.domain.KnowledgeQuery;
import com.carelink.clinical.domain.KnowledgeResult;
import com.carelink.clinical.domain.value.Sex;
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

/**
 * FR-CLN-06, FR-CLN-07.
 *
 * <p><b>Decisión de alcance, deliberada y distinta del resto de {@code clinical}:</b>
 * esta consulta NO se filtra por {@code service_id}. Todo otro endpoint clínico lo hace
 * (AC-06b), así que la excepción necesita justificarse: lo que devuelve el Motor de
 * Conocimiento son agregados sobre al menos {@code k} pacientes distintos, nunca la
 * historia de un paciente identificable, y su propósito explícito (§5.6) es aprender de
 * los casos previos de la INSTITUCIÓN — acotarlo al propio servicio lo dejaría respondiendo
 * "qué hizo mi servicio", que es una pregunta distinta y bastante menos útil. La
 * protección de privacidad acá es el k-anonimato (FR-CLN-07, ADR-007), no el aislamiento
 * por servicio. El aislamiento por TENANT sí se mantiene, sin excepción.
 *
 * <p>Cualquier rol clínico puede consultar ("Staff query past interventions", §5.6);
 * {@code AUDITOR} no, porque §4 le da explícitamente "no PHI read path" y estos
 * agregados salen de PHI.
 */
@RestController
@RequestMapping("/api/v1/knowledge")
public class KnowledgeController {

    private static final Set<String> CLINICAL_ROLES = Set.of(
            "PHYSICIAN", "NURSE", "SPECIALIST", "PHARMACIST", "LAB_TECH", "TENANT_ADMIN");

    private final SearchKnowledgeUseCase searchKnowledgeUseCase;
    private final ClinicalRequestScope requestScope;

    public KnowledgeController(SearchKnowledgeUseCase searchKnowledgeUseCase,
                                ClinicalRequestScope requestScope) {
        this.searchKnowledgeUseCase = searchKnowledgeUseCase;
        this.requestScope = requestScope;
    }

    @GetMapping("/search")
    public ResponseEntity<?> search(@AuthenticationPrincipal AuthenticatedPrincipal principal,
                                     @RequestParam(required = false) String diagnosisCie10,
                                     @RequestParam(required = false) String nandaCode,
                                     @RequestParam(required = false) Integer minAge,
                                     @RequestParam(required = false) Integer maxAge,
                                     @RequestParam(required = false) String sex) {
        if (principal == null || !CLINICAL_ROLES.contains(principal.role())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        Optional<TenantSlug> tenantSlug = requestScope.tenantSlug(principal);
        if (tenantSlug.isEmpty()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        KnowledgeQuery query;
        try {
            query = new KnowledgeQuery(diagnosisCie10, nandaCode, minAge, maxAge,
                    sex == null ? null : Sex.valueOf(sex));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        }

        KnowledgeResult result;
        try {
            result = searchKnowledgeUseCase.execute(tenantSlug.get(), query);
        } catch (UnsupportedOperationException e) {
            // Filtro de edad: no soportado sobre una columna cifrada. 501 y no 400 —
            // el request es válido, es el servidor el que todavía no puede responderlo.
            return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).body(Map.of("error", e.getMessage()));
        }

        Map<String, Object> body = new HashMap<>();
        body.put("kAnonymityThreshold", result.kAnonymityThreshold());

        if (result.suppressed()) {
            // FR-CLN-07: "datos insuficientes" explícito, NUNCA una lista vacía que se
            // pueda leer como "no hay casos previos". Son dos hechos clínicos distintos
            // y el contrato de la API los distingue en vez de dejarlo a la UI.
            body.put("suppressed", true);
            body.put("results", List.of());
            body.put("message", "Datos insuficientes: existen casos previos, pero por debajo del umbral de "
                    + result.kAnonymityThreshold() + " pacientes distintos no se pueden mostrar sin "
                    + "riesgo de re-identificación.");
            return ResponseEntity.ok(body);
        }

        List<Map<String, Object>> rows = new ArrayList<>();
        for (KnowledgeResult.InterventionEffectiveness r : result.rows()) {
            Map<String, Object> m = new HashMap<>();
            m.put("nicCode", r.nicCode());
            m.put("nocCode", r.nocCode());
            m.put("interventionCount", r.interventionCount());
            m.put("distinctPatients", r.distinctPatients());
            m.put("averageEffectiveness", r.averageEffectiveness());
            rows.add(m);
        }
        body.put("suppressed", false);
        body.put("results", rows);
        if (rows.isEmpty()) {
            body.put("message", "No hay casos previos que coincidan con estos criterios.");
        }
        return ResponseEntity.ok(body);
    }
}
