package com.carelink.identity.domain.port;

import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Optional;

/** Port for JWT signing and verification keys. */
public interface JwtKeyProvider {

    /**
     * Provides private key when locally available.
     *
     * @return private key if available
     */
    Optional<RSAPrivateKey> getPrivateKey();

    /**
     * Resolves public key by key id.
     *
     * @param kid key id
     * @return public key if found
     */
    Optional<RSAPublicKey> getPublicKeyByKid(String kid);

    /**
     * Returns default key id for signing.
     *
     * @return key id if configured
     */
    Optional<String> getDefaultKid();

    /**
     * Signs JWT input and returns raw signature bytes.
     *
     * @param signingInput compact signing input
     * @param kid key id hint
     * @return signature bytes if signing is available
     */
    Optional<byte[]> sign(byte[] signingInput, String kid);
}
