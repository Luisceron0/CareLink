package com.carelink.scheduling.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data repository para PhysicianEntity.
 */
public interface SpringDataPhysicianRepository
        extends JpaRepository<PhysicianEntity, UUID> {

    /**
     * Busca una entidad de physician por id y tenant.
     *
     * @param id       identificador del physician
     * @param tenantId identificador del tenant
     * @return optional con la entidad si existe
     */
    Optional<PhysicianEntity> findByIdAndTenantId(UUID id,
                                                  UUID tenantId);
}
