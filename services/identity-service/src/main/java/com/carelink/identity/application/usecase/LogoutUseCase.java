package com.carelink.identity.application.usecase;

import com.carelink.identity.domain.port.SessionRepository;

/** Revokes refresh token sessions. */
public final class LogoutUseCase {

    /** Session repository port. */
    private final SessionRepository sessionRepository;

    /**
     * Builds logout use case.
     *
     * @param sessionRepositoryPort session repository
     */
    public LogoutUseCase(final SessionRepository sessionRepositoryPort) {
        this.sessionRepository = sessionRepositoryPort;
    }

    /**
     * Revokes session by refresh token.
     *
     * @param refreshToken refresh token
     */
    public void execute(final String refreshToken) {
        sessionRepository.findByRefreshToken(refreshToken)
                .ifPresent(s -> sessionRepository.deleteById(s.id()));
    }
}
