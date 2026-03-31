package com.carelink.identity.domain.port;

import com.carelink.identity.domain.Session;
import java.util.Optional;
import java.util.UUID;
import java.util.List;

/** Port for refresh session persistence. */
public interface SessionRepository {

    /**
     * Finds a session by refresh token.
     *
     * @param token refresh token
     * @return session if present
     */
    Optional<Session> findByRefreshToken(String token);

    /**
     * Persists a session.
     *
     * @param session session aggregate
     */
    void save(Session session);

    /**
     * Deletes session by id.
     *
     * @param id session id
     */
    void deleteById(UUID id);

    /**
     * Lists sessions by user id.
     *
     * @param userId user id
     * @return user sessions
     */
    List<Session> findByUserId(UUID userId);
}
