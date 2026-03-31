package com.carelink.scheduling.domain.exception;

import com.carelink.scheduling.domain.value.AppointmentStatus;

/**
 * Lanzada cuando se intenta una transición de estado inválida.
 */
public final class InvalidStatusTransitionException extends RuntimeException {

    /**
     * Constructor.
     *
     * @param from estado origen
     * @param to   estado destino
     */
    public InvalidStatusTransitionException(
            final AppointmentStatus from,
            final AppointmentStatus to) {
        super("Invalid status transition: " + from + " -> " + to);
    }
}
