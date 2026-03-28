package com.carelink.identity.domain;

import com.carelink.identity.domain.value.TenantSlug;
import java.time.OffsetDateTime;

public final class Tenant {
    private final java.util.UUID id;
    private final String name;
    private final TenantSlug slug;
    private final OffsetDateTime createdAt;

    public Tenant(java.util.UUID id, String name, TenantSlug slug, OffsetDateTime createdAt) {
        this.id = id;
        this.name = name;
        this.slug = slug;
        this.createdAt = createdAt;
    }

    public java.util.UUID id() { return id; }
    public String name() { return name; }
    public TenantSlug slug() { return slug; }
    public OffsetDateTime createdAt() { return createdAt; }
}
