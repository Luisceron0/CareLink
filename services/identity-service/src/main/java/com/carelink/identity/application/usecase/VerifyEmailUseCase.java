package com.carelink.identity.application.usecase;

import com.carelink.identity.domain.port.VerificationTokenRepository;
import java.util.UUID;

/** Verifies user email from token. */
public final class VerifyEmailUseCase {

    /** Verification token repository port. */
    private final VerificationTokenRepository tokenRepository;

    /**
     * Builds verify-email use case.
     *
     * @param tokenRepositoryPort verification token repository
     */
    public VerifyEmailUseCase(
            final VerificationTokenRepository tokenRepositoryPort) {
        this.tokenRepository = tokenRepositoryPort;
    }

    /**
     * Verifies token and returns related user id.
     *
     * @param token verification token
     * @return user id
     */
    public UUID execute(final String token) {
        return tokenRepository.findUserIdByToken(token)
                .map(userId -> {
                    tokenRepository.delete(token);
                    return userId;
                })
                .orElseThrow(
                    () -> new RuntimeException("Invalid verification token")
                );
    }
}
