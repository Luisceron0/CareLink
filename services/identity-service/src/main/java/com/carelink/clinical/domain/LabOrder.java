package com.carelink.clinical.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * FR-CLN-11. El resultado vive en el mismo record (y en la misma tabla) que la orden:
 * es uno a uno y nunca se leen por separado — mismo criterio que
 * {@link InterventionOutcome}, sin el motivo extra del índice compuesto.
 *
 * <p>{@code criticalValue} es un flag explícito que carga el laboratorio, no algo que
 * este sistema derive comparando el valor contra un rango: los rangos de referencia
 * dependen del método, el equipo y la población, y derivarlo acá sería inventar un
 * criterio clínico que nadie estableció.
 */
public record LabOrder(
        UUID id,
        UUID patientId,
        UUID clinicalEncounterId,
        UUID orderingPhysicianId,
        String testCode,
        String testName,
        OffsetDateTime orderedAt,
        String resultValue,
        String resultUnits,
        Boolean criticalValue,
        UUID resultedByUserId,
        OffsetDateTime resultedAt,
        String serviceId
) {
    public LabOrder {
        if (patientId == null) throw new IllegalArgumentException("LabOrder requiere patientId");
        if (clinicalEncounterId == null) {
            throw new IllegalArgumentException("LabOrder requiere clinicalEncounterId (trazabilidad)");
        }
        if (testCode == null || testCode.isBlank()) throw new IllegalArgumentException("LabOrder requiere testCode");
    }

    public boolean hasResult() {
        return resultedAt != null;
    }

    public boolean isCritical() {
        return Boolean.TRUE.equals(criticalValue);
    }
}
