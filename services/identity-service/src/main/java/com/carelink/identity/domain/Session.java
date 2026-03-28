package com.carelink.identity.domain;

import java.time.OffsetDateTime;

public final class Session {
    private final java.util.UUID id;
    private final java.util.UUID userId;
    private final String refreshToken;
    private final OffsetDateTime createdAt;

    public Session(java.util.UUID id, java.util.UUID userId, String refreshToken, OffsetDateTime createdAt) {
        this.id = id;
        this.userId = userId;
        this.refreshToken = refreshToken;
        this.createdAt = createdAt;
    }

    public java.util.UUID id() { return id; }
    public java.util.UUID userId() { return userId; }
    public String refreshToken() { return refreshToken; }
    public OffsetDateTime createdAt() { return createdAt; }
}
