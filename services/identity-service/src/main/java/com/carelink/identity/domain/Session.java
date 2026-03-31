package com.carelink.identity.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Refresh token session aggregate.
 *
 * @param id session id
 * @param userId user id
 * @param refreshToken refresh token value
 * @param createdAt creation timestamp
 */
public record Session(
        UUID id,
        UUID userId,
        String refreshToken,
        OffsetDateTime createdAt) {
}
