package com.carelink.clinical.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * FR-CLN-09. {@code clinicalEncounterId} es obligatorio: una prescripción sin encounter
 * de origen no es trazable, y la trazabilidad completa es exactamente lo que este
 * requisito pide. {@code interconsultationId} es opcional — una prescripción puede nacer
 * de un encounter normal, sin interconsulta de por medio.
 */
public record Prescription(
        UUID id,
        UUID patientId,
        UUID clinicalEncounterId,
        UUID interconsultationId,
        UUID prescriberUserId,
        String medication,
        String dosage,
        String instructions,
        /** FR-CLN-12: frecuencia, duración y vía. `frequency` es texto libre — cifrado. */
        String frequency,
        Integer durationDays,
        String route,
        /**
         * Clase farmacológica, EN CLARO: es lo que permite detectar el conflicto de
         * "misma clase activa" con una consulta en vez de descifrar cada prescripción
         * del paciente. Dato de catálogo, no identificador — mismo criterio que
         * `diagnosis_cie10`.
         */
        String medicationClass,
        /** Total de dosis prescritas. Denominador del índice de adherencia; opcional. */
        Integer totalDoses,
        OffsetDateTime prescribedAt,
        String serviceId
) {
    public Prescription {
        if (clinicalEncounterId == null) {
            throw new IllegalArgumentException("Prescription requiere clinicalEncounterId (trazabilidad, FR-CLN-09)");
        }
        if (medication == null || medication.isBlank()) {
            throw new IllegalArgumentException("Prescription requiere medication");
        }
    }

    public boolean originatedInInterconsultation() {
        return interconsultationId != null;
    }
}
