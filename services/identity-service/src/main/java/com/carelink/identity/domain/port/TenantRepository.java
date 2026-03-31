package com.carelink.identity.domain.port;

import com.carelink.identity.domain.Tenant;
import java.util.Optional;

/** Port for tenant persistence operations. */
public interface TenantRepository {

    /**
     * Finds tenant by slug.
     *
     * @param slug tenant slug
     * @return tenant if present
     */
    Optional<Tenant> findBySlug(String slug);

    /**
     * Saves tenant aggregate.
     *
     * @param tenant tenant aggregate
     */
    void save(Tenant tenant);
}
