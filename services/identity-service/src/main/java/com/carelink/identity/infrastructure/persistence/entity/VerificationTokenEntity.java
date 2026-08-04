package com.carelink.identity.infrastructure.persistence.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Token de verificación de email.
 *
 * Se persiste el hash del token, nunca el token en claro: quien pueda leer esta
 * tabla no debe poder verificar cuentas ajenas con lo que encuentre ahí. Mismo
 * criterio que ADR-017 aplica a los refresh tokens.
 */
@Entity
@Table(name = "verification_tokens")
public class VerificationTokenEntity {
    @Id
    private UUID id;

    @Column(name = "token_hash", nullable = false, unique = true)
    private String tokenHash;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    public VerificationTokenEntity() {}

    public VerificationTokenEntity(UUID id, String tokenHash, UUID userId, OffsetDateTime createdAt) {
        this.id = id;
        this.tokenHash = tokenHash;
        this.userId = userId;
        this.createdAt = createdAt;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getTokenHash() { return tokenHash; }
    public void setTokenHash(String tokenHash) { this.tokenHash = tokenHash; }
    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
