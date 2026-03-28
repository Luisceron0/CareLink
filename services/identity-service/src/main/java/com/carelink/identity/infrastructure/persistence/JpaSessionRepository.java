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
        return jpa.findByRefreshToken(token).map(e -> new Session(e.getId(), e.getUserId(), e.getRefreshToken(), e.getCreatedAt()));
    }

    @Override
    public void save(Session session) {
        SessionEntity entity = new SessionEntity(session.id(), session.userId(), session.refreshToken(), session.createdAt());
        jpa.save(entity);
    }

    @Override
    public void deleteById(UUID id) {
        jpa.deleteById(id);
    }
}
