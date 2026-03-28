package com.carelink.identity.infrastructure.security;

import com.carelink.identity.domain.port.JwtKeyProvider;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.net.URL;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.Signature;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class VaultKeyProvider implements JwtKeyProvider {
    private static final Logger log = LoggerFactory.getLogger(VaultKeyProvider.class);
    private volatile RSAPrivateKey privateKey;
    private final Map<String, RSAPublicKey> publicCache = new ConcurrentHashMap<>();
    private final String kid;

    public VaultKeyProvider() {
        this(System.getenv("JWT_VAULT_PEM"), System.getenv("JWT_VAULT_PEM_PATH"), System.getenv("JWT_JWKS_URL"), System.getenv("JWT_VAULT_KID"));
    }

    // Visible for tests
    public VaultKeyProvider(String pem, String pemPath, String jwksUrl, String envKid) {
        String tmpKid = null;
        try {
            if (pem != null && !pem.isBlank()) {
                this.privateKey = parsePrivateKeyFromPem(pem);
                RSAPublicKey pub = derivePublicFromPrivate(privateKey);
                tmpKid = envKid != null && !envKid.isBlank() ? envKid : computeKid(pub);
                publicCache.put(tmpKid, pub);
            } else if (pemPath != null && !pemPath.isBlank()) {
                String content = Files.readString(Path.of(pemPath));
                this.privateKey = parsePrivateKeyFromPem(content);
                RSAPublicKey pub = derivePublicFromPrivate(privateKey);
                tmpKid = envKid != null && !envKid.isBlank() ? envKid : computeKid(pub);
                publicCache.put(tmpKid, pub);
            } else {
                tmpKid = envKid != null && !envKid.isBlank() ? envKid : null;
            }

            if (jwksUrl != null && !jwksUrl.isBlank()) {
                try {
                    JWKSet set = JWKSet.load(new URL(jwksUrl));
                    for (JWK jwk : set.getKeys()) {
                        if (jwk instanceof RSAKey) {
                            RSAKey r = (RSAKey) jwk;
                            publicCache.put(r.getKeyID(), r.toRSAPublicKey());
                        }
                    }
                } catch (Exception e) {
                    log.warn("Unable to preload JWKS from {}: {}", jwksUrl, e.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("VaultKeyProvider init failed: {}", e.getMessage());
        }
        this.kid = tmpKid;
    }

    private RSAPrivateKey parsePrivateKeyFromPem(String pem) throws Exception {
        String normalized = pem.replaceAll("-----BEGIN [A-Z ]+-----", "").replaceAll("-----END [A-Z ]+-----", "").replaceAll("\\s", "");
        byte[] der = Base64.getDecoder().decode(normalized);
        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(der);
        KeyFactory kf = KeyFactory.getInstance("RSA");
        return (RSAPrivateKey) kf.generatePrivate(spec);
    }

    private RSAPublicKey derivePublicFromPrivate(RSAPrivateKey priv) throws Exception {
        if (priv instanceof RSAPrivateCrtKey) {
            RSAPrivateCrtKey crt = (RSAPrivateCrtKey) priv;
            RSAPublicKeySpec pubSpec = new RSAPublicKeySpec(crt.getModulus(), crt.getPublicExponent());
            return (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(pubSpec);
        }
        throw new IllegalArgumentException("Private key is not RSA CRT key; cannot derive public exponent");
    }

    private String computeKid(RSAPublicKey pub) throws Exception {
        byte[] sha = MessageDigest.getInstance("SHA-256").digest(pub.getEncoded());
        return Base64.getUrlEncoder().withoutPadding().encodeToString(sha);
    }

    @Override
    public Optional<RSAPrivateKey> getPrivateKey() {
        return Optional.ofNullable(privateKey);
    }

    @Override
    public Optional<RSAPublicKey> getPublicKeyByKid(String kid) {
        return Optional.ofNullable(publicCache.get(kid));
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
