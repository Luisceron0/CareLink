package com.carelink.scheduling.domain.port;

import com.carelink.scheduling.domain.Appointment;

/**
 * Puerto para publicar eventos de dominio de appointments.
 */
public interface AppointmentEventPublisher {

    /**
     * Publica evento de cita reservada.
     *
     * @param appointment cita reservada
     */
    void publishBooked(Appointment appointment);

    /**
     * Publica evento de cita cancelada.
     *
     * @param appointment cita cancelada
     */
    void publishCancelled(Appointment appointment);

    /**
     * Publica evento de cambio de estado.
     *
     * @param appointment cita actualizada
     */
    void publishStatusChanged(Appointment appointment);
}
