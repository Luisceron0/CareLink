package com.carelink.identity.application.usecase;

import com.carelink.identity.domain.Session;
import com.carelink.identity.domain.User;
import com.carelink.identity.domain.port.PasswordEncoder;
import com.carelink.identity.domain.port.SessionRepository;
import com.carelink.identity.domain.port.UserRepository;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Authenticates a user and creates refresh sessions. */
public final class LoginUseCase {

    /** Default max concurrent sessions per user. */
    private static final int DEFAULT_MAX_CONCURRENT_SESSIONS = 3;

    /** User repository port. */
    private final UserRepository userRepository;

    /** Password encoder port. */
    private final PasswordEncoder passwordEncoder;

    /** Session repository port. */
    private final SessionRepository sessionRepository;

    /**
     * Builds login use case.
     *
     * @param userRepositoryPort user repository
     * @param passwordEncoderPort password encoder
     * @param sessionRepositoryPort session repository
     */
    public LoginUseCase(
            final UserRepository userRepositoryPort,
            final PasswordEncoder passwordEncoderPort,
            final SessionRepository sessionRepositoryPort) {
        this.userRepository = userRepositoryPort;
        this.passwordEncoder = passwordEncoderPort;
        this.sessionRepository = sessionRepositoryPort;
    }

    /**
     * Executes login flow.
     *
     * @param email user email
     * @param rawPassword raw password
     * @return persisted session
     */
    public Session execute(final String email, final CharSequence rawPassword) {
        final User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Invalid credentials"));

        if (!passwordEncoder.matches(rawPassword, user.password().value())) {
            throw new RuntimeException("Invalid credentials");
        }

        final int max = Integer.parseInt(System.getenv().getOrDefault(
            "MAX_CONCURRENT_SESSIONS",
            String.valueOf(DEFAULT_MAX_CONCURRENT_SESSIONS)
        ));
        var sessions = sessionRepository.findByUserId(user.id());
        while (sessions.size() >= max) {
            var oldest = sessions.remove(0);
            sessionRepository.deleteById(oldest.id());
        }

        final String refreshToken = UUID.randomUUID().toString();
        final Session session = new Session(
            UUID.randomUUID(),
            user.id(),
            refreshToken,
            OffsetDateTime.now()
        );
        sessionRepository.save(session);
        return session;
    }
}
