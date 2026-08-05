package com.carelink.clinical.domain.value;

/**
 * Clasificación de Triage Manchester — FR-CLN-03, SRS §5.4: "prioridad 1–5". Sin
 * modelar los nombres/colores tradicionales de Manchester (Inmediato/Muy urgente/
 * Urgente/Normal/No urgente) — el SRS solo pide el número, no un catálogo completo
 * que nadie pidió todavía (mismo criterio que `diagnosis_cie10` sin tabla CIE-10).
 */
public record TriagePriority(int value) {
    public TriagePriority {
        if (value < 1 || value > 5) {
            throw new IllegalArgumentException("TriagePriority debe estar entre 1 y 5 (Manchester): " + value);
        }
    }
}
