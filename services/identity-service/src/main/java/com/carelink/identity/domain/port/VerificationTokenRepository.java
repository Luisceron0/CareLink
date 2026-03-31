package com.carelink.identity.domain.port;

import java.util.Optional;
import java.util.UUID;

/** Port for verification token lifecycle. */
public interface VerificationTokenRepository {

    /**
     * Saves verification token.
     *
     * @param token verification token
     * @param userId user id
     */
    void save(String token, UUID userId);

    /**
     * Resolves user id from token.
     *
     * @param token verification token
     * @return user id if found
     */
    Optional<UUID> findUserIdByToken(String token);

    /**
     * Deletes verification token.
     *
     * @param token verification token
     */
    void delete(String token);
}
