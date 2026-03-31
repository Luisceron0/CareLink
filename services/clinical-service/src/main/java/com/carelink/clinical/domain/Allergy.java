package com.carelink.clinical.domain;

import java.util.Objects;
import java.util.UUID;

/**
 * Alergia declarada del paciente.
 *
 * @param id identificador
 * @param patientId identificador del paciente
 * @param substance sustancia alergénica
 * @param reaction reacción reportada
 */
public record Allergy(UUID id,
                      UUID patientId,
                      String substance,
                      String reaction) {

    /**
     * Constructor canónico.
     *
     * @param id identificador
     * @param patientId paciente
     * @param substance sustancia
     * @param reaction reacción
     */
    public Allergy {
        Objects.requireNonNull(id);
        Objects.requireNonNull(patientId);
        Objects.requireNonNull(substance);
        Objects.requireNonNull(reaction);
    }
}
