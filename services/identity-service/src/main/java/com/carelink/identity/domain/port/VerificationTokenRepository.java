package com.carelink.identity.domain.port;

import java.util.Optional;
import java.util.UUID;

public interface VerificationTokenRepository {
    void save(String token, UUID userId);
    Optional<UUID> findUserIdByToken(String token);
    void delete(String token);
}
