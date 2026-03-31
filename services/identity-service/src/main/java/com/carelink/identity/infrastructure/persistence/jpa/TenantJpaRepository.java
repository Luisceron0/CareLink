package com.carelink.identity.infrastructure.persistence.jpa;

import com.carelink.identity.infrastructure.persistence.entity.TenantEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

/** JPA repository for tenant entities. */
public interface TenantJpaRepository extends JpaRepository<TenantEntity, UUID> {

    /**
     * Finds a tenant by slug.
     *
     * @param slug tenant slug
     * @return matching tenant if present
     */
    Optional<TenantEntity> findBySlug(String slug);
}
