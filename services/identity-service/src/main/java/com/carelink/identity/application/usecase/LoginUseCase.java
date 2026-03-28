package com.carelink.identity.application.usecase;

import com.carelink.identity.domain.Session;
import com.carelink.identity.domain.User;
import com.carelink.identity.domain.value.HashedPassword;
import com.carelink.identity.domain.port.UserRepository;
import com.carelink.identity.domain.port.PasswordEncoder;
import com.carelink.identity.domain.port.SessionRepository;
import java.time.OffsetDateTime;
import java.util.UUID;

public class LoginUseCase {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final SessionRepository sessionRepository;

    public LoginUseCase(UserRepository userRepository, PasswordEncoder passwordEncoder, SessionRepository sessionRepository) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.sessionRepository = sessionRepository;
    }

    public Session execute(String email, CharSequence rawPassword) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Invalid credentials"));

        if (!passwordEncoder.matches(rawPassword, user.password().value())) {
            throw new RuntimeException("Invalid credentials");
        }

        String refreshToken = UUID.randomUUID().toString();
        Session session = new Session(UUID.randomUUID(), user.id(), refreshToken, OffsetDateTime.now());
        sessionRepository.save(session);
        return session;
    }
}
