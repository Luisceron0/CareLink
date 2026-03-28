package com.carelink.identity.domain.port;

import com.carelink.identity.domain.Session;
import java.util.Optional;
import java.util.UUID;

public interface SessionRepository {
    Optional<Session> findByRefreshToken(String token);
    void save(Session session);
    void deleteById(UUID id);
}
