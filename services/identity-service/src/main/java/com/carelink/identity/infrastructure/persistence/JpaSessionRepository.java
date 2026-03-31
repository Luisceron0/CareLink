package com.carelink.identity.infrastructure.persistence;

import com.carelink.identity.domain.Session;
import com.carelink.identity.domain.port.SessionRepository;
import com.carelink.identity.infrastructure.persistence.entity.SessionEntity;
import com.carelink.identity.infrastructure.persistence.jpa.SessionJpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public final class JpaSessionRepository implements SessionRepository {

    /** Default refresh token TTL in days. */
    private static final long DEFAULT_REFRESH_TTL_DAYS = 7L;

    /** Spring Data adapter for session persistence. */
    private final SessionJpaRepository jpa;

    /**
     * Builds the adapter.
     *
     * @param jpaRepository spring data repository
     */
    public JpaSessionRepository(final SessionJpaRepository jpaRepository) {
        this.jpa = jpaRepository;
    }

    @Override
    public Optional<Session> findByRefreshToken(final String token) {
        final String hashed =
            com.carelink.identity.infrastructure.security.TokenHasher
                .hash(token);
        final var opt = jpa.findByRefreshToken(hashed);
        if (opt.isEmpty()) {
            return Optional.empty();
        }
        final var entity = opt.get();
        final long ttlDays = Long.parseLong(
            System.getenv().getOrDefault(
                "REFRESH_TOKEN_TTL_DAYS",
                String.valueOf(DEFAULT_REFRESH_TTL_DAYS)
            )
        );
        final java.time.OffsetDateTime expiry = entity.getCreatedAt()
            .plusDays(ttlDays);
        if (java.time.OffsetDateTime.now().isAfter(expiry)) {
            jpa.deleteById(entity.getId());
            return Optional.empty();
        }
        return Optional.of(new Session(
            entity.getId(),
            entity.getUserId(),
            token,
            entity.getCreatedAt()
        ));
    }

    @Override
    public void save(final Session session) {
        final String hashed =
            com.carelink.identity.infrastructure.security.TokenHasher.hash(
                session.refreshToken()
            );
        final SessionEntity entity = new SessionEntity(
            session.id(),
            session.userId(),
            hashed,
            session.createdAt()
        );
        jpa.save(entity);
    }

    @Override
    public void deleteById(final UUID id) {
        jpa.deleteById(id);
    }

    @Override
    public java.util.List<Session> findByUserId(final UUID userId) {
        final var entities = jpa.findByUserIdOrderByCreatedAtAsc(userId);
        final var sessions = new java.util.ArrayList<Session>();
        for (SessionEntity entity : entities) {
            sessions.add(new Session(
                entity.getId(),
                entity.getUserId(),
                entity.getRefreshToken(),
                entity.getCreatedAt()
            ));
        }
        return sessions;
    }
}
