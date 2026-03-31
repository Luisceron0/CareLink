package com.carelink.scheduling.domain.port;

import com.carelink.scheduling.domain.Appointment;
import com.carelink.scheduling.domain.value.AppointmentStatus;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Puerto de persistencia para Appointment.
 */
public interface AppointmentRepository {

    /**
     * Busca citas que conflijan para un physician en el rango [start, end).
     *
     * @param physicianId identificador del physician
     * @param start       instante de inicio (inclusive)
     * @param end         instante de fin (exclusive)
     * @return lista de citas que confligen en el rango
     */
    List<Appointment> findConflicts(UUID physicianId,
                                    LocalDateTime start,
                                    LocalDateTime end);

    /**
     * Persiste la appointment y la retorna.
     *
     * @param appointment cita a persistir
     * @return la cita persistida
     */
    Appointment save(Appointment appointment);

    /**
     * Busca una appointment por su identificador.
     *
     * @param id identificador de la cita
     * @return optional con la cita si existe
     */
    Optional<Appointment> findById(UUID id);

    /**
     * Busca una appointment por tenant e id.
     *
     * @param tenantId tenant propietario
     * @param id       identificador de la cita
     * @return optional con la cita si existe para el tenant
     */
    Optional<Appointment> findByTenantAndId(UUID tenantId, UUID id);

    /**
     * Lista appointments de un tenant con filtros opcionales.
     *
     * @param tenantId    tenant propietario
     * @param physicianId filtro opcional por médico
     * @param date        filtro opcional por fecha de inicio
     * @param status      filtro opcional por estado
     * @return lista de appointments filtradas
     */
    List<Appointment> listByTenant(UUID tenantId,
                                   UUID physicianId,
                                   LocalDate date,
                                   AppointmentStatus status);
}
