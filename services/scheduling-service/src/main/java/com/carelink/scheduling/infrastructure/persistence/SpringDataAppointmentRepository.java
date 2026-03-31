package com.carelink.scheduling.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repositorio Spring Data para `AppointmentEntity`.
 */
public interface SpringDataAppointmentRepository
        extends JpaRepository<AppointmentEntity, UUID> {

    /**
     * Busca citas activas de un médico.
     *
     * @param physicianId identificador del médico
     * @param statuses    estados activos para colisión
     * @return citas activas del médico
     */
    List<AppointmentEntity> findByPhysicianIdAndStatusIn(
            UUID physicianId,
            List<String> statuses
    );

    /**
     * Busca una cita por tenant e id.
     *
     * @param tenantId tenant propietario
     * @param id       identificador de la cita
     * @return cita si existe
     */
    Optional<AppointmentEntity> findByTenantIdAndId(UUID tenantId, UUID id);

    /**
     * Lista citas de un tenant ordenadas por inicio.
     *
     * @param tenantId tenant propietario
     * @return citas del tenant
     */
    List<AppointmentEntity> findByTenantIdOrderBySlotStartAsc(UUID tenantId);
}
