package com.carelink.identity.application.usecase;

import com.carelink.identity.domain.Session;
import com.carelink.identity.domain.port.SessionRepository;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Rotates refresh tokens. */
public final class RefreshTokenUseCase {

    /** Session repository port. */
    private final SessionRepository sessionRepository;

    /**
     * Builds refresh-token use case.
     *
     * @param sessionRepositoryPort session repository
     */
    public RefreshTokenUseCase(final SessionRepository sessionRepositoryPort) {
        this.sessionRepository = sessionRepositoryPort;
    }

    /**
     * Rotates a valid refresh token.
     *
     * @param refreshToken refresh token
     * @return newly created session
     */
    public Session execute(final String refreshToken) {
        final Session existing = sessionRepository
            .findByRefreshToken(refreshToken)
            .orElseThrow(
                () -> new RuntimeException("Invalid refresh token")
            );

        final String newRefresh = UUID.randomUUID().toString();
        final Session next = new Session(
            UUID.randomUUID(),
            existing.userId(),
            newRefresh,
            OffsetDateTime.now()
        );
        sessionRepository.save(next);
        sessionRepository.deleteById(existing.id());
        return next;
    }
}
