package com.carelink.clinical.application;

import java.util.Objects;

/**
 * Objeto de datos para contenido clinico de un encuentro.
 *
 * @param chiefComplaint motivo principal de consulta
 * @param physicalExam hallazgos de examen fisico
 * @param treatmentPlan plan terapeutico
 * @param followUpInstructions indicaciones de seguimiento
 */
public record EncounterContent(String chiefComplaint,
                               String physicalExam,
                               String treatmentPlan,
                               String followUpInstructions) {

    /**
     * Constructor canonico con campos obligatorios.
     */
    public EncounterContent {
        Objects.requireNonNull(chiefComplaint);
        Objects.requireNonNull(physicalExam);
        Objects.requireNonNull(treatmentPlan);
        Objects.requireNonNull(followUpInstructions);
    }
}
