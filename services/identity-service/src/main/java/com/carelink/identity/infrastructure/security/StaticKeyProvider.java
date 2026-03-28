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

public class StaticKeyProvider implements JwtKeyProvider {
    private final RSAPrivateKey privateKey;
    private final RSAPublicKey publicKey;
    private final String kid;

    public StaticKeyProvider() {
        try {
            // Dev-friendly: generate an in-memory keypair if none provided via Vault
            RSAKey rsa = new RSAKeyGenerator(2048)
                    .keyID(UUID.randomUUID().toString())
                    .generate();
            this.privateKey = rsa.toRSAPrivateKey();
            this.publicKey = rsa.toRSAPublicKey();
            this.kid = rsa.getKeyID();
        } catch (Exception e) {
            throw new RuntimeException("Unable to initialize StaticKeyProvider", e);
        }
    }

    @Override
    public Optional<RSAPrivateKey> getPrivateKey() {
        return Optional.ofNullable(privateKey);
    }

    @Override
    public Optional<RSAPublicKey> getPublicKeyByKid(String kid) {
        if (this.kid != null && this.kid.equals(kid)) return Optional.of(publicKey);
        return Optional.empty();
    }

    @Override
    public Optional<String> getDefaultKid() {
        return Optional.ofNullable(kid);
    }

    @Override
    public Optional<byte[]> sign(byte[] signingInput, String kid) {
        try {
            if (this.privateKey == null) return Optional.empty();
            if (kid != null && this.kid != null && !this.kid.equals(kid)) return Optional.empty();
            Signature sig = Signature.getInstance("SHA256withRSA");
            sig.initSign(privateKey);
            sig.update(signingInput);
            return Optional.of(sig.sign());
        } catch (Exception e) {
            throw new RuntimeException("Signing failed", e);
        }
    }
}
