package com.carelink.identity.infrastructure.persistence;

import com.carelink.identity.domain.Tenant;
import com.carelink.identity.domain.value.TenantSlug;
import com.carelink.identity.domain.port.TenantRepository;
import com.carelink.identity.infrastructure.persistence.entity.TenantEntity;
import com.carelink.identity.infrastructure.persistence.jpa.TenantJpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public final class JpaTenantRepository implements TenantRepository {

    /** Spring Data adapter for tenant persistence. */
    private final TenantJpaRepository jpa;

    /**
     * Builds the adapter.
     *
     * @param tenantJpaRepository spring data repository
     */
    public JpaTenantRepository(final TenantJpaRepository tenantJpaRepository) {
        this.jpa = tenantJpaRepository;
    }

    @Override
    public Optional<Tenant> findBySlug(final String slug) {
        return jpa.findBySlug(slug)
            .map(entity -> new Tenant(
                entity.getId(),
                entity.getName(),
                new TenantSlug(entity.getSlug()),
                entity.getCreatedAt()
            ));
    }

    @Override
    public void save(final Tenant tenant) {
        final TenantEntity entity = new TenantEntity(
            tenant.id(),
            tenant.name(),
            tenant.slug().value(),
            tenant.createdAt()
        );
        jpa.save(entity);
    }
}
