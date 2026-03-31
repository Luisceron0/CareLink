package com.carelink.clinical.infrastructure.security;

import com.carelink.clinical.domain.port.EncryptionPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.UUID;

/**
 * Adaptador de cifrado PHI con clave maestra gestionada por Vault.
 */
@Component
@Primary
@ConditionalOnProperty(name = VaultEncryptionAdapter.MASTER_KEY_ENV)
public final class VaultEncryptionAdapter implements EncryptionPort {

    /** Variable de entorno para clave maestra local. */
    public static final String MASTER_KEY_ENV = "CLINICAL_MASTER_KEY_BASE64";

    /** Servicio criptográfico. */
    private final AesGcmCipherService cipherService;

    /**
     * Constructor por defecto usando variable de entorno.
     */
    public VaultEncryptionAdapter() {
        this(new AesGcmCipherService(readMasterKeyFromEnvironment()));
    }

    /**
     * Constructor para pruebas/inyección explícita.
     *
     * @param cipherServiceArg servicio criptográfico
     */
    public VaultEncryptionAdapter(final AesGcmCipherService cipherServiceArg) {
        this.cipherService = Objects.requireNonNull(cipherServiceArg);
    }

    @Override
    public String encrypt(final UUID tenantId, final String plaintext) {
        return cipherService.encrypt(tenantId, plaintext);
    }

    @Override
    public String decrypt(final UUID tenantId, final String ciphertext) {
        return cipherService.decrypt(tenantId, ciphertext);
    }

    /**
     * Obtiene la clave maestra desde entorno para desarrollo local.
     *
     * @return clave base64
     */
    private static String readMasterKeyFromEnvironment() {
        final String masterKey = System.getenv(MASTER_KEY_ENV);
        if (masterKey == null || masterKey.isBlank()) {
            throw new IllegalStateException(
                    MASTER_KEY_ENV + " is required for PHI encryption"
            );
        }
        return masterKey;
    }
}
