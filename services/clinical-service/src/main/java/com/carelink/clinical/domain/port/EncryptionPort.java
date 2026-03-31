package com.carelink.clinical.domain.port;

import java.util.UUID;

/**
 * Puerto de cifrado para datos PHI.
 */
public interface EncryptionPort {

    /**
     * Cifra texto para un tenant.
     *
     * @param tenantId tenant
     * @param plaintext texto plano
     * @return texto cifrado
     */
    String encrypt(UUID tenantId, String plaintext);

    /**
     * Descifra texto para un tenant.
     *
     * @param tenantId tenant
     * @param ciphertext texto cifrado
     * @return texto descifrado
     */
    String decrypt(UUID tenantId, String ciphertext);
}
