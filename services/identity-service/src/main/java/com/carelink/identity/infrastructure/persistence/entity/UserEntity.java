package com.carelink.identity.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

/** JPA entity for users table. */
@Entity
@Table(name = "users")
public class UserEntity {

    /** Primary identifier. */
    @Id
    private UUID id;

    /** Tenant identifier. */
    @Column(name = "tenant_id")
    private UUID tenantId;

    /** User email. */
    private String email;

    /** User role. */
    private String role;

    /** Encoded password. */
    @Column(name = "password")
    private String password;

    /** Creation timestamp. */
    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    /** Default constructor required by JPA. */
    public UserEntity() {
    }

    /**
     * Builds entity instance.
     *
     * @param userId id
     * @param userTenantId tenant id
     * @param userEmail email
     * @param userRole role
     * @param userPassword password
     * @param createdAtValue created at
     */
    public UserEntity(
            final UUID userId,
            final UUID userTenantId,
            final String userEmail,
            final String userRole,
            final String userPassword,
            final OffsetDateTime createdAtValue) {
        this.id = userId;
        this.tenantId = userTenantId;
        this.email = userEmail;
        this.role = userRole;
        this.password = userPassword;
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
     * @param userId id
     */
    public void setId(final UUID userId) {
        this.id = userId;
    }

    /**
     * Gets tenant id.
     *
     * @return tenant id
     */
    public UUID getTenantId() {
        return tenantId;
    }

    /**
     * Sets tenant id.
     *
     * @param userTenantId tenant id
     */
    public void setTenantId(final UUID userTenantId) {
        this.tenantId = userTenantId;
    }

    /**
     * Gets email.
     *
     * @return email
     */
    public String getEmail() {
        return email;
    }

    /**
     * Sets email.
     *
     * @param userEmail email
     */
    public void setEmail(final String userEmail) {
        this.email = userEmail;
    }

    /**
     * Gets role.
     *
     * @return role
     */
    public String getRole() {
        return role;
    }

    /**
     * Sets role.
     *
     * @param userRole role
     */
    public void setRole(final String userRole) {
        this.role = userRole;
    }

    /**
     * Gets password.
     *
     * @return encoded password
     */
    public String getPassword() {
        return password;
    }

    /**
     * Sets password.
     *
     * @param userPassword encoded password
     */
    public void setPassword(final String userPassword) {
        this.password = userPassword;
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
