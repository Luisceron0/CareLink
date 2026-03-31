package com.carelink.scheduling.domain;

import com.carelink.scheduling.domain.value.AppointmentStatus;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Entidad de dominio que representa una cita (appointment).
 * <p>
 * El identificador se genera en el dominio mediante UUID v4 usando
 * el método de fábrica {@link #create}.
 *
 * @param id          identificador de la cita
 * @param tenantId    identificador del tenant
 * @param physicianId identificador del médico
 * @param patientId   identificador del paciente
 * @param start       instante de inicio
 * @param duration    duración de la cita
 * @param status      estado actual de la cita
 */
public record Appointment(
        UUID id,
        UUID tenantId,
        UUID physicianId,
        UUID patientId,
        LocalDateTime start,
        Duration duration,
        AppointmentStatus status
) {

    /**
     * Crea una nueva Appointment con id generado y estado PENDING.
     *
     * @param tenantId    identificador del tenant
     * @param physicianId identificador del médico
     * @param patientId   identificador del paciente
     * @param start       instante de inicio
     * @param duration    duración de la cita
     * @return instancia inmutable de Appointment
     */
    public static Appointment create(
            final UUID tenantId,
            final UUID physicianId,
            final UUID patientId,
            final LocalDateTime start,
            final Duration duration) {
        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(physicianId);
        Objects.requireNonNull(patientId);
        Objects.requireNonNull(start);
        Objects.requireNonNull(duration);
        return new Appointment(
                UUID.randomUUID(),
                tenantId,
                physicianId,
                patientId,
                start,
                duration,
                AppointmentStatus.PENDING
        );
    }

    /**
     * Devuelve una copia con el nuevo estado validando la transición.
     *
     * @param next estado objetivo
     * @return nueva instancia con el estado actualizado
     */
    public Appointment withStatus(final AppointmentStatus next) {
        if (!this.status.isValidTransition(next)) {
            throw new com.carelink.scheduling.domain.exception
                    .InvalidStatusTransitionException(
                    this.status,
                    next
            );
        }

        return new Appointment(
                this.id,
                this.tenantId,
                this.physicianId,
                this.patientId,
                this.start,
                this.duration,
                next
        );
    }
}
