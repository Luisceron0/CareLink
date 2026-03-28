package com.carelink.identity.infrastructure.persistence;

import com.carelink.identity.domain.Session;
import com.carelink.identity.domain.port.SessionRepository;
import com.carelink.identity.infrastructure.persistence.entity.SessionEntity;
import com.carelink.identity.infrastructure.persistence.jpa.SessionJpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public class JpaSessionRepository implements SessionRepository {
    private final SessionJpaRepository jpa;

    public JpaSessionRepository(SessionJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Optional<Session> findByRefreshToken(String token) {
        // Tokens are stored hashed. Hash incoming token and look up. Also enforce TTL.
        String hashed = com.carelink.identity.infrastructure.security.TokenHasher.hash(token);
        var opt = jpa.findByRefreshToken(hashed);
        if (opt.isEmpty()) return Optional.empty();
        var e = opt.get();
        long ttlDays = Long.parseLong(System.getenv().getOrDefault("REFRESH_TOKEN_TTL_DAYS", "7"));
        java.time.OffsetDateTime expiry = e.getCreatedAt().plusDays(ttlDays);
        if (java.time.OffsetDateTime.now().isAfter(expiry)) {
            // expired: remove stale session
            jpa.deleteById(e.getId());
            return Optional.empty();
        }
        return Optional.of(new Session(e.getId(), e.getUserId(), token, e.getCreatedAt()));
    }

    @Override
    public void save(Session session) {
        // store hashed refresh token for safety
        String hashed = com.carelink.identity.infrastructure.security.TokenHasher.hash(session.refreshToken());
        SessionEntity entity = new SessionEntity(session.id(), session.userId(), hashed, session.createdAt());
        jpa.save(entity);
    }

    @Override
    public void deleteById(UUID id) {
        jpa.deleteById(id);
    }

    @Override
    public java.util.List<Session> findByUserId(UUID userId) {
        var list = jpa.findByUserIdOrderByCreatedAtAsc(userId);
        var out = new java.util.ArrayList<Session>();
        for (SessionEntity e : list) {
            // note: we cannot recover the raw refresh token here, so expose stored hash as token placeholder
            out.add(new Session(e.getId(), e.getUserId(), e.getRefreshToken(), e.getCreatedAt()));
        }
        return out;
    }
}
