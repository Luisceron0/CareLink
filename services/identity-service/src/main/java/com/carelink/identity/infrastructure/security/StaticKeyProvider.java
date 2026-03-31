package com.carelink.identity.infrastructure.security;

import com.carelink.identity.domain.port.JwtKeyProvider;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jose.jwk.RSAKey;
// JwtKeyProvider beans are configured by JwtKeyProviderConfig

import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.Signature;
import java.util.Optional;
import java.util.UUID;

public final class StaticKeyProvider implements JwtKeyProvider {

    /** Generated RSA key size. */
    private static final int RSA_KEY_SIZE = 2048;

    /** In-memory private key for local signing. */
    private final RSAPrivateKey privateKey;

    /** In-memory public key for local verification. */
    private final RSAPublicKey publicKey;

    /** Key identifier associated with the generated key pair. */
    private final String kid;

    /**
     * Creates an ephemeral key provider.
     */
    public StaticKeyProvider() {
        try {
            final RSAKey rsa = new RSAKeyGenerator(RSA_KEY_SIZE)
                    .keyID(UUID.randomUUID().toString())
                    .generate();
            this.privateKey = rsa.toRSAPrivateKey();
            this.publicKey = rsa.toRSAPublicKey();
            this.kid = rsa.getKeyID();
        } catch (Exception e) {
            throw new RuntimeException(
                "Unable to initialize StaticKeyProvider",
                e
            );
        }
    }

    @Override
    public Optional<RSAPrivateKey> getPrivateKey() {
        return Optional.ofNullable(privateKey);
    }

    @Override
    public Optional<RSAPublicKey> getPublicKeyByKid(final String keyId) {
        if (this.kid != null && this.kid.equals(keyId)) {
            return Optional.of(publicKey);
        }
        return Optional.empty();
    }

    @Override
    public Optional<String> getDefaultKid() {
        return Optional.ofNullable(kid);
    }

    @Override
        public Optional<byte[]> sign(
            final byte[] signingInput,
            final String keyId) {
        try {
            if (this.privateKey == null) {
                return Optional.empty();
            }
            if (keyId != null && this.kid != null && !this.kid.equals(keyId)) {
                return Optional.empty();
            }
            final Signature sig = Signature.getInstance("SHA256withRSA");
            sig.initSign(privateKey);
            sig.update(signingInput);
            return Optional.of(sig.sign());
        } catch (Exception e) {
            throw new RuntimeException("Signing failed", e);
        }
    }
}
