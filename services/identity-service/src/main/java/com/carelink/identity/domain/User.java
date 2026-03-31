package com.carelink.identity.domain;

import com.carelink.identity.domain.value.Email;
import com.carelink.identity.domain.value.HashedPassword;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Identity user aggregate.
 *
 * @param id user id
 * @param tenantId tenant id
 * @param email email value object
 * @param role role string
 * @param password hashed password value object
 * @param createdAt creation timestamp
 */
public record User(
        UUID id,
        UUID tenantId,
        Email email,
        String role,
        HashedPassword password,
        OffsetDateTime createdAt) {
}
