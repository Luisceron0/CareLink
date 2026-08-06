package com.carelink.clinical.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * FR-CLN-04 (intervención NIC) + FR-CLN-05 (su resultado NOC).
 *
 * <p>{@code outcome} es nulo hasta que alguien registra el resultado — una intervención
 * se ejecuta primero y se evalúa después, no en el mismo instante.
 *
 * <p>{@code diagnosisCie10} y {@code nandaCode} se guardan acá y no se sacan por JOIN
 * contra los encounters del paciente: el Motor de Conocimiento agrupa por ellos, y
 * derivarlos de un encounter ataría cada intervención a un encounter abierto —
 * exactamente el vínculo que FR-CLN-04 dice que NO existe ("no necesariamente ligado a
 * un encounter activo").
 */
public record HealthIntervention(
        UUID id,
        UUID diaryEntryId,
        UUID patientId,
        String nandaCode,
        String nicCode,
        String diagnosisCie10,
        String description,
        OffsetDateTime performedAt,
        InterventionOutcome outcome,
        String serviceId
) {
    public HealthIntervention {
        if (nicCode == null || nicCode.isBlank()) {
            throw new IllegalArgumentException("HealthIntervention requiere nicCode");
        }
    }

    public boolean hasOutcome() {
        return outcome != null;
    }
}
