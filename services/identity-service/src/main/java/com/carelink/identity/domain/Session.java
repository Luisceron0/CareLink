package com.carelink.identity.domain;

import java.time.OffsetDateTime;

public record Session(java.util.UUID id, java.util.UUID userId, String refreshToken, OffsetDateTime createdAt) {}
