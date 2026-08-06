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
