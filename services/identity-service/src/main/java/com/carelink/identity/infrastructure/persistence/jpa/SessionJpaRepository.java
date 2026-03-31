package com.carelink.identity.infrastructure.persistence.jpa;

import com.carelink.identity.infrastructure.persistence.entity.SessionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

/** JPA repository for session entities. */
public interface SessionJpaRepository
    extends JpaRepository<SessionEntity, UUID> {

    /**
     * Finds a session by stored refresh token hash.
     *
     * @param token hashed token
     * @return matching session if present
     */
    Optional<SessionEntity> findByRefreshToken(String token);

    /**
     * Lists all sessions of a user sorted by creation time.
     *
     * @param userId user identifier
     * @return ordered list of sessions
     */
    java.util.List<SessionEntity> findByUserIdOrderByCreatedAtAsc(UUID userId);
}
