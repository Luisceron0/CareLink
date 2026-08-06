package com.carelink.clinical.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

/** FR-CLN-12 — dispensación registrada por el PHARMACIST. Alimenta el índice de adherencia. */
public record DispensationRecord(
        UUID id,
        UUID prescriptionId,
        UUID patientId,
        UUID pharmacistUserId,
        int dosesDispensed,
        OffsetDateTime dispensedAt,
        String serviceId
) {
    public DispensationRecord {
        if (dosesDispensed <= 0) {
            throw new IllegalArgumentException("dosesDispensed debe ser positivo: " + dosesDispensed);
        }
    }
}
