package com.carelink.identity.application.usecase;

import com.carelink.identity.domain.port.VerificationTokenRepository;
import java.util.UUID;

public class VerifyEmailUseCase {
    private final VerificationTokenRepository tokenRepository;

    public VerifyEmailUseCase(VerificationTokenRepository tokenRepository) {
        this.tokenRepository = tokenRepository;
    }

    public UUID execute(String token) {
        return tokenRepository.findUserIdByToken(token)
                .map(userId -> {
                    tokenRepository.delete(token);
                    return userId;
                })
                .orElseThrow(() -> new RuntimeException("Invalid verification token"));
    }
}
