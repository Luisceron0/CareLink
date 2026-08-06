package com.carelink.clinical.domain;

import com.carelink.clinical.domain.value.Sex;

/**
 * FR-CLN-06 — los filtros de una búsqueda en el Motor de Conocimiento. Todos opcionales,
 * pero al menos uno de {@code diagnosisCie10}/{@code nandaCode} tiene que venir: sin
 * ningún criterio clínico la consulta devolvería "todas las intervenciones del tenant",
 * que no es una búsqueda de conocimiento sino un volcado.
 */
public record KnowledgeQuery(
        String diagnosisCie10,
        String nandaCode,
        Integer minAge,
        Integer maxAge,
        Sex sex
) {
    public KnowledgeQuery {
        boolean sinCriterioClinico = (diagnosisCie10 == null || diagnosisCie10.isBlank())
                && (nandaCode == null || nandaCode.isBlank());
        if (sinCriterioClinico) {
            throw new IllegalArgumentException(
                    "La búsqueda requiere al menos un diagnóstico CIE-10 o un código NANDA");
        }
        if (minAge != null && minAge < 0) throw new IllegalArgumentException("minAge no puede ser negativo");
        if (minAge != null && maxAge != null && minAge > maxAge) {
            throw new IllegalArgumentException("minAge no puede ser mayor que maxAge");
        }
    }
}
