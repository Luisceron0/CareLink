package com.carelink.identity.domain.port;

import com.carelink.identity.domain.Tenant;
import java.util.Optional;

public interface TenantRepository {
    Optional<Tenant> findBySlug(String slug);
    void save(Tenant tenant);
}
