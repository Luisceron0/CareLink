package com.carelink.identity.application;

import com.carelink.identity.application.usecase.LoginUseCase;
import com.carelink.identity.domain.User;
import com.carelink.identity.domain.value.Email;
import com.carelink.identity.domain.value.HashedPassword;
import com.carelink.identity.domain.port.UserRepository;
import com.carelink.identity.domain.port.PasswordEncoder;
import com.carelink.identity.domain.port.SessionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class InMemoryUserRepoLogin implements UserRepository {
    private java.util.Map<String, User> map = new java.util.HashMap<>();
    @Override public Optional<User> findByEmail(String email) { return Optional.ofNullable(map.get(email)); }
    @Override public Optional<User> findById(java.util.UUID id) { return map.values().stream().filter(u -> u.id().equals(id)).findFirst(); }
    @Override public void save(User user) { map.put(user.email().value(), user); }
}

class InMemorySessionRepo implements SessionRepository {
    public java.util.Map<UUID, com.carelink.identity.domain.Session> map = new java.util.HashMap<>();
    @Override public Optional<com.carelink.identity.domain.Session> findByRefreshToken(String token) {
        return map.values().stream().filter(s -> s.refreshToken().equals(token)).findFirst();
    }
    @Override public void save(com.carelink.identity.domain.Session session) { map.put(session.id(), session); }
    @Override public void deleteById(UUID id) { map.remove(id); }
    @Override public java.util.List<com.carelink.identity.domain.Session> findByUserId(UUID userId) {
        return map.values().stream().filter(s -> s.userId().equals(userId))
                .sorted(java.util.Comparator.comparing(com.carelink.identity.domain.Session::createdAt))
                .collect(java.util.stream.Collectors.toList());
    }
}

class InMemoryPasswordEncoderLogin implements PasswordEncoder {
    @Override public String encode(CharSequence rawPassword) { return "enc:" + rawPassword; }
    @Override public boolean matches(CharSequence rawPassword, String encodedPassword) { return encodedPassword.equals("enc:" + rawPassword); }
}

public class LoginUseCaseTest {
    private InMemoryUserRepoLogin userRepo;
    private InMemorySessionRepo sessionRepo;
    private InMemoryPasswordEncoderLogin passwordEncoder;
    private LoginUseCase useCase;

    @BeforeEach
    void setUp() {
        userRepo = new InMemoryUserRepoLogin();
        sessionRepo = new InMemorySessionRepo();
        passwordEncoder = new InMemoryPasswordEncoderLogin();
        useCase = new LoginUseCase(userRepo, passwordEncoder, sessionRepo);
    }

    @Test
    void successfulLoginCreatesSession() {
        User u = new User(UUID.randomUUID(), UUID.randomUUID(), new Email("u@example.com"), "TENANT_ADMIN", new HashedPassword("enc:secret"), OffsetDateTime.now());
        userRepo.save(u);

        com.carelink.identity.domain.Session s = useCase.execute("u@example.com", "secret");
        assertNotNull(s.id());
        assertEquals(u.id(), s.userId());
        assertTrue(sessionRepo.map.containsKey(s.id()));
    }

    @Test
    void invalidCredentialsThrow() {
        assertThrows(RuntimeException.class, () -> useCase.execute("no@one", "x"));
    }
}
