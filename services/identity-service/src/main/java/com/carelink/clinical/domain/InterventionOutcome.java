package com.carelink.clinical.domain;

import java.time.OffsetDateTime;

/**
 * FR-CLN-05 — el resultado (NOC) y la efectividad de una intervención, que es lo que
 * alimenta el Motor de Conocimiento (§5.6).
 *
 * <p>Es su propio record aunque en la base viva en las mismas columnas que
 * {@link HealthIntervention} — §10 lo modela como entidad propia y el dominio lo
 * respeta; la fusión es una decisión de ALMACENAMIENTO, tomada para que el índice
 * compuesto de ADR-006 pueda existir como un solo índice (ver
 * {@code tenant_template.sql}). El dominio no tiene por qué heredar esa forma.
 */
public record InterventionOutcome(
        String nocCode,
        int effectiveness,
        String notes,
        OffsetDateTime recordedAt
) {
    public InterventionOutcome {
        if (effectiveness < 1 || effectiveness > 5) {
            throw new IllegalArgumentException("effectiveness debe estar entre 1 y 5: " + effectiveness);
        }
    }
}
