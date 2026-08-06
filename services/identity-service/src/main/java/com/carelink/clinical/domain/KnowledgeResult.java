package com.carelink.clinical.domain;

import java.util.List;

/**
 * FR-CLN-06/FR-CLN-07 — el resultado de una búsqueda en el Motor de Conocimiento.
 *
 * <p>{@code suppressed} es un estado PROPIO, no "lista vacía": FR-CLN-07 exige que la
 * interfaz muestre "datos insuficientes" y nunca un resultado vacío que se pueda leer
 * como "no hay casos previos". Son dos hechos clínicos distintos —"no encontré nada" vs.
 * "encontré algo pero no puedo mostrarlo sin arriesgar re-identificación"— y colapsarlos
 * en una lista vacía haría imposible cumplir ese requisito desde la capa de arriba.
 * Por eso el tipo los distingue en vez de dejarlo a criterio de cada consumidor.
 */
public record KnowledgeResult(
        List<InterventionEffectiveness> rows,
        boolean suppressed,
        int kAnonymityThreshold
) {
    public KnowledgeResult {
        rows = rows == null ? List.of() : List.copyOf(rows);
    }

    /** Resultado suprimido por k-anonimato (FR-CLN-07): había datos, pero por debajo del umbral. */
    public static KnowledgeResult suppressed(int threshold) {
        return new KnowledgeResult(List.of(), true, threshold);
    }

    /** Resultado normal — puede venir vacío, y eso sí significa "no hay casos previos". */
    public static KnowledgeResult of(List<InterventionEffectiveness> rows, int threshold) {
        return new KnowledgeResult(rows, false, threshold);
    }

    /**
     * Una fila agregada: para esta intervención NIC, cuántas veces se aplicó, con qué
     * efectividad promedio y sobre cuántos pacientes DISTINTOS (que es la cifra que el
     * umbral de k-anonimato controla, no el total de intervenciones — diez
     * intervenciones sobre un mismo paciente siguen siendo un solo paciente
     * identificable).
     */
    public record InterventionEffectiveness(
            String nicCode,
            String nocCode,
            long interventionCount,
            long distinctPatients,
            double averageEffectiveness
    ) {}
}
