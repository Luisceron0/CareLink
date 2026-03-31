package com.carelink.identity.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

/** JPA entity for tenants table. */
@Entity
@Table(name = "tenants")
public class TenantEntity {

    /** Primary identifier. */
    @Id
    private UUID id;

    /** Tenant display name. */
    private String name;

    /** URL-safe tenant slug. */
    private String slug;

    /** Creation timestamp. */
    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    /** Default constructor required by JPA. */
    public TenantEntity() {
    }

    /**
     * Builds entity instance.
     *
     * @param tenantId id
     * @param tenantName name
     * @param tenantSlug slug
     * @param createdAtValue created at
     */
    public TenantEntity(
            final UUID tenantId,
            final String tenantName,
            final String tenantSlug,
            final OffsetDateTime createdAtValue) {
        this.id = tenantId;
        this.name = tenantName;
        this.slug = tenantSlug;
        this.createdAt = createdAtValue;
    }

    /**
     * Gets id.
     *
     * @return id
     */
    public UUID getId() {
        return id;
    }

    /**
     * Sets id.
     *
     * @param tenantId id
     */
    public void setId(final UUID tenantId) {
        this.id = tenantId;
    }

    /**
     * Gets name.
     *
     * @return name
     */
    public String getName() {
        return name;
    }

    /**
     * Sets name.
     *
     * @param tenantName name
     */
    public void setName(final String tenantName) {
        this.name = tenantName;
    }

    /**
     * Gets slug.
     *
     * @return slug
     */
    public String getSlug() {
        return slug;
    }

    /**
     * Sets slug.
     *
     * @param tenantSlug slug
     */
    public void setSlug(final String tenantSlug) {
        this.slug = tenantSlug;
    }

    /**
     * Gets creation timestamp.
     *
     * @return created at
     */
    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    /**
     * Sets creation timestamp.
     *
     * @param createdAtValue created at
     */
    public void setCreatedAt(final OffsetDateTime createdAtValue) {
        this.createdAt = createdAtValue;
    }
}
