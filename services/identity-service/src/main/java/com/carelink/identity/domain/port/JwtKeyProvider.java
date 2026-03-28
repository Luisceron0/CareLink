package com.carelink.identity.domain.port;

import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Optional;

public interface JwtKeyProvider {
    Optional<RSAPrivateKey> getPrivateKey();
    Optional<RSAPublicKey> getPublicKeyByKid(String kid);
    Optional<String> getDefaultKid();
    // Sign the JWT signing input (headerBase64 + "." + payloadBase64) and
    // return raw signature bytes. Implementations that cannot sign should
    // return Optional.empty().
    Optional<byte[]> sign(byte[] signingInput, String kid);
}
