package com.carelink.clinical.infrastructure.security;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Objects;
import java.util.UUID;

/**
 * Servicio criptográfico AES-256-GCM con derivación de clave por tenant.
 */
public final class AesGcmCipherService {

    /** Transformación de cifrado. */
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";

    /** Nombre de algoritmo simétrico. */
    private static final String ALGORITHM = "AES";

    /** Tamaño de IV para GCM en bytes. */
    private static final int IV_LENGTH = 12;

    /** Tamaño de tag GCM en bits. */
    private static final int TAG_LENGTH_BITS = 128;

    /** Tamaño esperado de clave maestra AES-256 en bytes. */
    private static final int MASTER_KEY_LENGTH = 32;

    /** Prefijo de versión del payload cifrado. */
    private static final String VERSION_PREFIX = "v1:";

    /** RNG criptográficamente seguro. */
    private final SecureRandom secureRandom;

    /** Clave maestra en bytes. */
    private final byte[] masterKey;

    /**
     * Crea servicio desde clave maestra base64.
     *
     * @param base64MasterKey clave base64 (32 bytes)
     */
    public AesGcmCipherService(final String base64MasterKey) {
        Objects.requireNonNull(base64MasterKey);
        this.masterKey = Base64.getDecoder().decode(base64MasterKey);
        if (masterKey.length != MASTER_KEY_LENGTH) {
            throw new IllegalArgumentException(
                "CLINICAL_MASTER_KEY_BASE64 must decode to "
                    + MASTER_KEY_LENGTH
                    + " bytes"
            );
        }
        this.secureRandom = new SecureRandom();
    }

    /**
     * Cifra texto para un tenant específico.
     *
     * @param tenantId tenant
     * @param plaintext texto plano
     * @return payload versionado base64
     */
    public String encrypt(final UUID tenantId, final String plaintext) {
        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(plaintext);
        try {
            final byte[] iv = new byte[IV_LENGTH];
            secureRandom.nextBytes(iv);

            final Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            final SecretKeySpec key = new SecretKeySpec(
                    deriveTenantKey(tenantId),
                    ALGORITHM
            );
            final GCMParameterSpec gcmSpec =
                    new GCMParameterSpec(TAG_LENGTH_BITS, iv);
            cipher.init(Cipher.ENCRYPT_MODE, key, gcmSpec);

            final byte[] encrypted =
                    cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
                final byte[] payload = ByteBuffer
                    .allocate(iv.length + encrypted.length)
                    .put(iv)
                    .put(encrypted)
                    .array();
            return VERSION_PREFIX + Base64.getEncoder().encodeToString(payload);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException(
                    "Unable to encrypt PHI payload",
                    exception
            );
        }
    }

    /**
     * Descifra texto para un tenant específico.
     *
     * @param tenantId tenant
     * @param ciphertext texto cifrado
     * @return texto plano
     */
    public String decrypt(final UUID tenantId, final String ciphertext) {
        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(ciphertext);

        // Compatibilidad para datos previos no cifrados.
        if (!ciphertext.startsWith(VERSION_PREFIX)) {
            return ciphertext;
        }

        try {
                final String encoded =
                    ciphertext.substring(VERSION_PREFIX.length());
            final byte[] payload = Base64.getDecoder().decode(encoded);
            if (payload.length <= IV_LENGTH) {
                throw new IllegalArgumentException("Invalid encrypted payload");
            }

            final byte[] iv = new byte[IV_LENGTH];
            final byte[] encrypted = new byte[payload.length - IV_LENGTH];
            System.arraycopy(payload, 0, iv, 0, IV_LENGTH);
                System.arraycopy(
                    payload,
                    IV_LENGTH,
                    encrypted,
                    0,
                    encrypted.length
                );

            final Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            final SecretKeySpec key = new SecretKeySpec(
                    deriveTenantKey(tenantId),
                    ALGORITHM
            );
            final GCMParameterSpec gcmSpec =
                    new GCMParameterSpec(TAG_LENGTH_BITS, iv);
            cipher.init(Cipher.DECRYPT_MODE, key, gcmSpec);

            final byte[] plain = cipher.doFinal(encrypted);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException(
                    "Unable to decrypt PHI payload",
                    exception
            );
        }
    }

    /**
     * Deriva una clave de 256 bits por tenant usando SHA-256.
     *
     * @param tenantId tenant
     * @return clave derivada
     */
    private byte[] deriveTenantKey(final UUID tenantId) {
        try {
            final MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(masterKey);
            digest.update(tenantId.toString().getBytes(StandardCharsets.UTF_8));
            return digest.digest();
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException(
                    "Unable to derive tenant key",
                    exception
            );
        }
    }
}
