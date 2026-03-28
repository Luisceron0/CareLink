package com.carelink.identity.application;

import com.carelink.identity.application.usecase.VerifyEmailUseCase;
import com.carelink.identity.domain.port.VerificationTokenRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import java.util.Optional;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class InMemoryVerificationTokenRepoSimple implements VerificationTokenRepository {
    private java.util.Map<String, UUID> map = new java.util.HashMap<>();
    @Override public void save(String token, UUID userId) { map.put(token, userId); }
    @Override public Optional<UUID> findUserIdByToken(String token) { return Optional.ofNullable(map.get(token)); }
    @Override public void delete(String token) { map.remove(token); }
}

public class VerifyEmailUseCaseTest {
    private InMemoryVerificationTokenRepoSimple repo;
    private VerifyEmailUseCase useCase;

    @BeforeEach
    void setUp() {
        repo = new InMemoryVerificationTokenRepoSimple();
        useCase = new VerifyEmailUseCase(repo);
    }

    @Test
    void verifyConsumesToken() {
        UUID userId = UUID.randomUUID();
        String token = "tok-123";
        repo.save(token, userId);

        UUID result = useCase.execute(token);
        assertEquals(userId, result);
        assertFalse(repo.findUserIdByToken(token).isPresent());
    }

    @Test
    void invalidTokenThrows() {
        assertThrows(RuntimeException.class, () -> useCase.execute("nope"));
    }
}
