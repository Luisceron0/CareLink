package com.carelink.scheduling.infrastructure.web;

import com.carelink.scheduling.domain.Appointment;
import com.carelink.scheduling.domain.value.AppointmentStatus;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * DTOs para la API de appointments.
 */
public final class AppointmentDtos {

    private AppointmentDtos() {
    }

    /**
     * Request de creación de cita.
     *
     * @param physicianId     identificador del médico
     * @param patientId       identificador del paciente
     * @param slotStart       inicio de la cita
     * @param durationMinutes duración en minutos
     */
    public record CreateAppointmentRequest(
            UUID physicianId,
            UUID patientId,
            LocalDateTime slotStart,
            long durationMinutes
    ) {
    }

    /**
     * Request de cambio de estado.
     *
     * @param status nuevo estado de la cita
     */
    public record UpdateStatusRequest(AppointmentStatus status) {
    }

    /**
     * Response de cita.
     *
     * @param id              identificador
     * @param tenantId        tenant propietario
     * @param physicianId     médico
     * @param patientId       paciente
     * @param slotStart       inicio del slot
     * @param durationMinutes duración en minutos
     * @param status          estado actual
     */
    public record AppointmentResponse(
            UUID id,
            UUID tenantId,
            UUID physicianId,
            UUID patientId,
            LocalDateTime slotStart,
            long durationMinutes,
            AppointmentStatus status
    ) {
    }

    /**
     * Convierte entidad de dominio a response.
     *
     * @param appointment cita de dominio
     * @return DTO de respuesta
     */
    public static AppointmentResponse toResponse(
            final Appointment appointment
    ) {
        return new AppointmentResponse(
                appointment.id(),
                appointment.tenantId(),
                appointment.physicianId(),
                appointment.patientId(),
                appointment.start(),
                appointment.duration().toMinutes(),
                appointment.status()
        );
    }

    /**
     * Crea una duración desde minutos.
     *
     * @param minutes duración en minutos
     * @return objeto duración
     */
    public static Duration toDuration(final long minutes) {
        return Duration.ofMinutes(minutes);
    }
}
