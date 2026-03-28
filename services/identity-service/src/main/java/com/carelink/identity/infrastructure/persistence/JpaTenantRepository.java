package com.carelink.identity.infrastructure.persistence;

import com.carelink.identity.domain.Tenant;
import com.carelink.identity.domain.value.TenantSlug;
import com.carelink.identity.domain.port.TenantRepository;
import com.carelink.identity.infrastructure.persistence.entity.TenantEntity;
import com.carelink.identity.infrastructure.persistence.jpa.TenantJpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class JpaTenantRepository implements TenantRepository {
    private final TenantJpaRepository jpa;

    public JpaTenantRepository(TenantJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Optional<Tenant> findBySlug(String slug) {
        return jpa.findBySlug(slug).map(e -> new Tenant(e.getId(), e.getName(), new TenantSlug(e.getSlug()), e.getCreatedAt()));
    }

    @Override
    public void save(Tenant tenant) {
        TenantEntity entity = new TenantEntity(tenant.id(), tenant.name(), tenant.slug().value(), tenant.createdAt());
        jpa.save(entity);
    }
}
