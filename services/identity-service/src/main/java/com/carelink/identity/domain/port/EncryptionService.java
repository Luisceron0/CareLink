package com.carelink.identity.domain.port;

/**
 * Cifrado de columnas PHI — AES-256-GCM, IV aleatorio por operación, clave por tenant
 * (ADR-003, SRS §8.3). {@code tenantSlug} entra en cada llamada porque la clave
 * depende de qué tenant es el dato, no es un secreto global compartido entre todos.
 */
public interface EncryptionService {
    /** Devuelve base64(IV + ciphertext+tag) — el formato de almacenamiento de §8.3. */
    String encrypt(String plaintext, String tenantSlug);

    String decrypt(String stored, String tenantSlug);
}
