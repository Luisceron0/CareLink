package com.carelink.identity.domain.port;

import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Optional;

public interface JwtKeyProvider {
    Optional<RSAPrivateKey> getPrivateKey();
    Optional<RSAPublicKey> getPublicKeyByKid(String kid);
    Optional<String> getDefaultKid();
}
