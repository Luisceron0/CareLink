package com.carelink.identity.domain;

import com.carelink.identity.domain.value.TenantSlug;
import java.time.OffsetDateTime;

public record Tenant(java.util.UUID id, String name, TenantSlug slug, OffsetDateTime createdAt) {}
