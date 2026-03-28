package com.carelink.identity.application;

import com.carelink.identity.application.usecase.LogoutUseCase;
import com.carelink.identity.domain.Session;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import java.time.OffsetDateTime;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class InMemorySessionRepoLogout implements com.carelink.identity.domain.port.SessionRepository {
    public java.util.Map<UUID, Session> map = new java.util.HashMap<>();
    @Override public java.util.Optional<Session> findByRefreshToken(String token) { return map.values().stream().filter(s -> s.refreshToken().equals(token)).findFirst(); }
    @Override public void save(Session session) { map.put(session.id(), session); }
    @Override public void deleteById(UUID id) { map.remove(id); }
}

public class LogoutUseCaseTest {
    private InMemorySessionRepoLogout repo;
    private LogoutUseCase useCase;

    @BeforeEach
    void setUp() {
        repo = new InMemorySessionRepoLogout();
        useCase = new LogoutUseCase(repo);
    }

    @Test
    void logoutDeletesSession() {
        Session s = new Session(UUID.randomUUID(), UUID.randomUUID(), "rt-1", OffsetDateTime.now());
        repo.save(s);

        useCase.execute("rt-1");
        assertFalse(repo.map.values().stream().anyMatch(sess -> sess.refreshToken().equals("rt-1")));
    }
}
