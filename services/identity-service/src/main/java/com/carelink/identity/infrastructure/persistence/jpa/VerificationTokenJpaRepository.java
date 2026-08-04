package com.carelink.identity.infrastructure.persistence.jpa;

import com.carelink.identity.infrastructure.persistence.entity.VerificationTokenEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface VerificationTokenJpaRepository extends JpaRepository<VerificationTokenEntity, UUID> {
    Optional<VerificationTokenEntity> findByTokenHash(String tokenHash);
    void deleteByTokenHash(String tokenHash);
}
