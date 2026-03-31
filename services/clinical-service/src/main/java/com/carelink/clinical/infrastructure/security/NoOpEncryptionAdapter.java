package com.carelink.clinical.infrastructure.security;

import com.carelink.clinical.domain.port.EncryptionPort;

import java.util.UUID;

/**
 * Adaptador no-op para entornos sin clave Vault configurada.
 */
public final class NoOpEncryptionAdapter implements EncryptionPort {

    @Override
    public String encrypt(final UUID tenantId, final String plaintext) {
        return plaintext;
    }

    @Override
    public String decrypt(final UUID tenantId, final String ciphertext) {
        return ciphertext;
    }
}
