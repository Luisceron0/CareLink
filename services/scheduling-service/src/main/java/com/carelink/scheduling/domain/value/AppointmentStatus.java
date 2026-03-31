package com.carelink.scheduling.domain.value;

/**
 * Enum que representa los estados de una cita y valida transiciones.
 */
public enum AppointmentStatus {

    /** Cita pendiente, aún no confirmada. */
    PENDING,

    /** Cita confirmada por el paciente o el sistema. */
    CONFIRMED,

    /** Cita en progreso en el momento actual. */
    IN_PROGRESS,

    /** Cita completada correctamente. */
    COMPLETED,

    /** Cita cancelada. */
    CANCELLED,

    /** Paciente no se presentó (no-show). */
    NO_SHOW;

    /**
     * Valida si la transición al siguiente estado es permitida.
     *
    * @param next estado destino
    * @return {@code true} si la transición está permitida,
    *         {@code false} en caso contrario
     */
    public boolean isValidTransition(final AppointmentStatus next) {
        if (next == null) {
            return false;
        }

        return switch (this) {
            case PENDING -> next == CONFIRMED || next == CANCELLED;
            case CONFIRMED -> next == IN_PROGRESS || next == CANCELLED;
            case IN_PROGRESS -> next == COMPLETED
                    || next == CANCELLED
                    || next == NO_SHOW;
            case COMPLETED, CANCELLED, NO_SHOW -> false;
        };
    }
}
