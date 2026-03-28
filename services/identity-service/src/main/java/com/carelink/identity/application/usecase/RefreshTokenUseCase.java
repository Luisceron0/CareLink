package com.carelink.identity.application.usecase;

import com.carelink.identity.domain.Session;
import com.carelink.identity.domain.port.SessionRepository;
import java.time.OffsetDateTime;
import java.util.UUID;

public class RefreshTokenUseCase {
    private final SessionRepository sessionRepository;

    public RefreshTokenUseCase(SessionRepository sessionRepository) {
        this.sessionRepository = sessionRepository;
    }

    public Session execute(String refreshToken) {
        Session existing = sessionRepository.findByRefreshToken(refreshToken)
                .orElseThrow(() -> new RuntimeException("Invalid refresh token"));

        // Create a new session (rotation): issue new refresh token, persist, then delete old
        String newRefresh = UUID.randomUUID().toString();
        Session next = new Session(UUID.randomUUID(), existing.userId(), newRefresh, OffsetDateTime.now());
        sessionRepository.save(next);
        sessionRepository.deleteById(existing.id());
        return next;
    }
}
