package com.carelink.identity.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

/** JPA entity for sessions table. */
@Entity
@Table(name = "sessions")
public class SessionEntity {

    /** Primary identifier. */
    @Id
    private UUID id;

    /** User identifier. */
    @Column(name = "user_id")
    private UUID userId;

    /** Stored refresh token hash. */
    @Column(name = "refresh_token")
    private String refreshToken;

    /** Creation timestamp. */
    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    /** Default constructor required by JPA. */
    public SessionEntity() {
    }

    /**
     * Builds entity instance.
     *
     * @param sessionId id
     * @param sessionUserId user id
     * @param storedRefreshToken stored token
     * @param createdAtValue created at
     */
    public SessionEntity(
            final UUID sessionId,
            final UUID sessionUserId,
            final String storedRefreshToken,
            final OffsetDateTime createdAtValue) {
        this.id = sessionId;
        this.userId = sessionUserId;
        this.refreshToken = storedRefreshToken;
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
     * @param sessionId id
     */
    public void setId(final UUID sessionId) {
        this.id = sessionId;
    }

    /**
     * Gets user id.
     *
     * @return user id
     */
    public UUID getUserId() {
        return userId;
    }

    /**
     * Sets user id.
     *
     * @param sessionUserId user id
     */
    public void setUserId(final UUID sessionUserId) {
        this.userId = sessionUserId;
    }

    /**
     * Gets refresh token.
     *
     * @return refresh token
     */
    public String getRefreshToken() {
        return refreshToken;
    }

    /**
     * Sets refresh token.
     *
     * @param storedRefreshToken refresh token
     */
    public void setRefreshToken(final String storedRefreshToken) {
        this.refreshToken = storedRefreshToken;
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
