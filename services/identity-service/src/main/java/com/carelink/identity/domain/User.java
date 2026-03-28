package com.carelink.identity.domain;

import com.carelink.identity.domain.value.Email;
import com.carelink.identity.domain.value.HashedPassword;
import java.time.OffsetDateTime;

public record User(java.util.UUID id, java.util.UUID tenantId, Email email, String role, HashedPassword password, OffsetDateTime createdAt) {}
