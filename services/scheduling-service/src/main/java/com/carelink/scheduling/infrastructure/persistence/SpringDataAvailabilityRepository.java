package com.carelink.scheduling.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

/**
 * Spring Data repository para AvailabilityBlockEntity.
 */
public interface SpringDataAvailabilityRepository
        extends JpaRepository<AvailabilityBlockEntity, UUID> {
    /**
     * Busca entidades de availability por physician.
     *
     * @param physicianId identificador del physician
     * @return lista de entidades
     */
    List<AvailabilityBlockEntity> findByPhysicianId(UUID physicianId);
}
