package com.carelink.clinical.domain;

import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Medicamento activo de un paciente.
 *
 * @param id identificador
 * @param patientId identificador del paciente
 * @param medicationName nombre del medicamento
 * @param startedAt fecha de inicio
 */
public record ActiveMedication(UUID id,
                               UUID patientId,
                               String medicationName,
                               LocalDate startedAt) {

    /**
     * Constructor canónico.
     *
     * @param id identificador
     * @param patientId paciente
     * @param medicationName nombre
     * @param startedAt fecha de inicio
     */
    public ActiveMedication {
        Objects.requireNonNull(id);
        Objects.requireNonNull(patientId);
        Objects.requireNonNull(medicationName);
        Objects.requireNonNull(startedAt);
    }
}
