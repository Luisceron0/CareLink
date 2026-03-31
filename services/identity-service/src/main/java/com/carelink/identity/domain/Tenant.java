package com.carelink.identity.domain;

import com.carelink.identity.domain.value.TenantSlug;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Tenant aggregate root.
 *
 * @param id tenant id
 * @param name tenant name
 * @param slug tenant slug
 * @param createdAt creation timestamp
 */
public record Tenant(
        UUID id,
        String name,
        TenantSlug slug,
        OffsetDateTime createdAt) {
}
