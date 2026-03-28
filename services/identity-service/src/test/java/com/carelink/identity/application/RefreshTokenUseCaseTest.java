package com.carelink.identity.application;

import com.carelink.identity.application.usecase.RefreshTokenUseCase;
import com.carelink.identity.domain.Session;
import com.carelink.identity.domain.value.HashedPassword;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class InMemorySessionRepoRefresh implements com.carelink.identity.domain.port.SessionRepository {
    public java.util.Map<UUID, Session> map = new java.util.HashMap<>();
    @Override public Optional<Session> findByRefreshToken(String token) { return map.values().stream().filter(s -> s.refreshToken().equals(token)).findFirst(); }
    @Override public void save(Session session) { map.put(session.id(), session); }
    @Override public void deleteById(UUID id) { map.remove(id); }
    @Override public java.util.List<Session> findByUserId(UUID userId) { return map.values().stream().filter(s -> s.userId().equals(userId)).sorted(java.util.Comparator.comparing(Session::createdAt)).collect(java.util.stream.Collectors.toList()); }
}

public class RefreshTokenUseCaseTest {
    private InMemorySessionRepoRefresh repo;
    private RefreshTokenUseCase useCase;

    @BeforeEach
    void setUp() {
        repo = new InMemorySessionRepoRefresh();
        useCase = new RefreshTokenUseCase(repo);
    }

    @Test
    void rotatesToken() {
        Session s = new Session(UUID.randomUUID(), UUID.randomUUID(), "rt-1", OffsetDateTime.now());
        repo.save(s);

        Session next = useCase.execute("rt-1");
        assertNotNull(next);
        assertNotEquals(s.id(), next.id());
        assertNotEquals(s.refreshToken(), next.refreshToken());
        assertTrue(repo.map.containsKey(next.id()));
    }

    @Test
    void invalidRefreshThrows() {
        assertThrows(RuntimeException.class, () -> useCase.execute("nope"));
    }
}
