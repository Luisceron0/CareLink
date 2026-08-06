package com.carelink.clinical.domain;

import com.carelink.clinical.domain.value.Shift;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * FR-CLN-04. Se asocia a Patient + fecha/turno, NO a un {@link ClinicalEncounter}
 * abierto: §10 es explícito en que el seguimiento de enfermería puede abarcar toda la
 * admisión, independiente de los límites de un encounter.
 *
 * <p>{@code vitalSigns} e {@code interventions} son las listas que llegaron con esta
 * entrada; una entrada puede tener ninguna de las dos (solo observaciones).
 */
public record HealthDiaryEntry(
        UUID id,
        UUID patientId,
        UUID nurseUserId,
        LocalDate entryDate,
        Shift shift,
        String observations,
        List<VitalSigns> vitalSigns,
        List<HealthIntervention> interventions,
        String serviceId,
        OffsetDateTime createdAt
) {
    public HealthDiaryEntry {
        if (patientId == null) throw new IllegalArgumentException("HealthDiaryEntry requiere patientId");
        if (nurseUserId == null) throw new IllegalArgumentException("HealthDiaryEntry requiere nurseUserId");
        if (entryDate == null) throw new IllegalArgumentException("HealthDiaryEntry requiere entryDate");
        if (shift == null) throw new IllegalArgumentException("HealthDiaryEntry requiere shift");
        vitalSigns = vitalSigns == null ? List.of() : List.copyOf(vitalSigns);
        interventions = interventions == null ? List.of() : List.copyOf(interventions);
    }
}
