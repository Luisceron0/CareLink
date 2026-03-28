package com.carelink.identity.domain;

import com.carelink.identity.domain.value.Email;
import com.carelink.identity.domain.value.HashedPassword;
import java.time.OffsetDateTime;

public final class User {
    private final java.util.UUID id;
    private final java.util.UUID tenantId;
    private final Email email;
    private final String role;
    private final HashedPassword password;
    private final OffsetDateTime createdAt;

    public User(java.util.UUID id, java.util.UUID tenantId, Email email, String role, HashedPassword password, OffsetDateTime createdAt) {
        this.id = id;
        this.tenantId = tenantId;
        this.email = email;
        this.role = role;
        this.password = password;
        this.createdAt = createdAt;
    }

    public java.util.UUID id() { return id; }
    public java.util.UUID tenantId() { return tenantId; }
    public Email email() { return email; }
    public String role() { return role; }
    public HashedPassword password() { return password; }
    public OffsetDateTime createdAt() { return createdAt; }
}
