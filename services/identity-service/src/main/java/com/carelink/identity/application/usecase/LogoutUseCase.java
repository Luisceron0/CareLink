package com.carelink.identity.application.usecase;

import com.carelink.identity.domain.port.SessionRepository;
import java.util.UUID;

public class LogoutUseCase {
    private final SessionRepository sessionRepository;

    public LogoutUseCase(SessionRepository sessionRepository) {
        this.sessionRepository = sessionRepository;
    }

    public void execute(String refreshToken) {
        sessionRepository.findByRefreshToken(refreshToken)
                .ifPresent(s -> sessionRepository.deleteById(s.id()));
    }
}
