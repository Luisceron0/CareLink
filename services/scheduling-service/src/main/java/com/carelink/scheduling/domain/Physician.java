package com.carelink.scheduling.domain;

import java.util.UUID;

/**
 * Representa un médico en el dominio de scheduling.
 *
 * @param id       identificador del physician
 * @param tenantId identificador del tenant al que pertenece
 * @param fullName nombre completo
 * @param specialty especialidad del médico
 */
public record Physician(
        UUID id,
        UUID tenantId,
        String fullName,
        String specialty) {
}
