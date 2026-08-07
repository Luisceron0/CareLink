package com.carelink.identity.infrastructure.persistence;

import com.carelink.identity.domain.port.VerificationTokenRepository;
import com.carelink.identity.infrastructure.persistence.entity.VerificationTokenEntity;
import com.carelink.identity.infrastructure.persistence.jpa.VerificationTokenJpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

/**
 * Adaptador JPA de {@link VerificationTokenRepository}.
 *
 * El token viaja en claro hacia el usuario (por email) pero se guarda hasheado.
 * El hash es SHA-256 sin sal a propósito: la búsqueda es por token exacto, así
 * que necesita ser determinista. Es aceptable porque el token es material
 * aleatorio de alta entropía generado por el sistema, no un secreto elegido por
 * una persona — no hay diccionario que atacar.
 */
@Repository
public class JpaVerificationTokenRepository implements VerificationTokenRepository {
    private final VerificationTokenJpaRepository jpa;

    public JpaVerificationTokenRepository(VerificationTokenJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public void save(String token, UUID userId) {
        VerificationTokenEntity entity = new VerificationTokenEntity(
                UUID.randomUUID(), hash(token), userId, OffsetDateTime.now());
        jpa.save(entity);
    }

    @Override
    public Optional<UUID> findUserIdByToken(String token) {
        return jpa.findByTokenHash(hash(token)).map(VerificationTokenEntity::getUserId);
    }

    @Override
    @Transactional
    public void delete(String token) {
        jpa.deleteByTokenHash(hash(token));
    }

    private static String hash(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] out = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(out);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 es obligatorio en toda JVM; si falta, el entorno está roto.
            throw new IllegalStateException("SHA-256 no disponible en esta JVM", e);
        }
    }
}
