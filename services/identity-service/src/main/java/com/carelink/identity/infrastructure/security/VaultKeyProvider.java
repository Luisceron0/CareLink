package com.carelink.identity.infrastructure.security;

import com.carelink.identity.domain.port.JwtKeyProvider;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
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

public final class VaultKeyProvider implements JwtKeyProvider {

    /** Class logger. */
    private static final Logger LOG = LoggerFactory.getLogger(
        VaultKeyProvider.class
    );

    /** Loaded private key for local signing fallback. */
    private volatile RSAPrivateKey privateKey;

    /** Public keys cache indexed by key identifier. */
    private final Map<String, RSAPublicKey> publicCache =
        new ConcurrentHashMap<>();

    /** Default key identifier. */
    private final String kid;

    /**
     * Builds provider from environment variables.
     */
    public VaultKeyProvider() {
        this(
            System.getenv("JWT_VAULT_PEM"),
            System.getenv("JWT_VAULT_PEM_PATH"),
            System.getenv("JWT_JWKS_URL"),
            System.getenv("JWT_VAULT_KID")
        );
    }

    /**
     * Builds provider from explicit values.
     *
     * @param pem private key in PEM format
     * @param pemPath path to PEM file
     * @param jwksUrl JWKS endpoint for public keys
     * @param envKid configured key id
     */
    public VaultKeyProvider(
            final String pem,
            final String pemPath,
            final String jwksUrl,
            final String envKid) {
        String tmpKid = null;
        try {
            if (pem != null && !pem.isBlank()) {
                this.privateKey = parsePrivateKeyFromPem(pem);
                final RSAPublicKey pub = derivePublicFromPrivate(privateKey);
                tmpKid = envKid != null && !envKid.isBlank()
                    ? envKid
                    : computeKid(pub);
                publicCache.put(tmpKid, pub);
            } else if (pemPath != null && !pemPath.isBlank()) {
                final String content = Files.readString(Path.of(pemPath));
                this.privateKey = parsePrivateKeyFromPem(content);
                final RSAPublicKey pub = derivePublicFromPrivate(privateKey);
                tmpKid = envKid != null && !envKid.isBlank()
                    ? envKid
                    : computeKid(pub);
                publicCache.put(tmpKid, pub);
            } else {
                tmpKid = envKid != null && !envKid.isBlank() ? envKid : null;
            }

            if (jwksUrl != null && !jwksUrl.isBlank()) {
                try {
                    final JWKSet set = JWKSet.load(new URL(jwksUrl));
                    for (JWK jwk : set.getKeys()) {
                        if (jwk instanceof RSAKey) {
                            final RSAKey rsaKey = (RSAKey) jwk;
                            publicCache.put(
                                rsaKey.getKeyID(),
                                rsaKey.toRSAPublicKey()
                            );
                        }
                    }
                } catch (Exception e) {
                    LOG.warn(
                        "Unable to preload JWKS from {}: {}",
                        jwksUrl,
                        e.getMessage()
                    );
                }
            }
        } catch (Exception e) {
            LOG.error("VaultKeyProvider init failed: {}", e.getMessage());
        }
        this.kid = tmpKid;
    }

    private RSAPrivateKey parsePrivateKeyFromPem(final String pem)
            throws Exception {
        final String normalized = pem
            .replaceAll("-----BEGIN [A-Z ]+-----", "")
            .replaceAll("-----END [A-Z ]+-----", "")
            .replaceAll("\\s", "");
        final byte[] der = Base64.getDecoder().decode(normalized);
        final PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(der);
        final KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        return (RSAPrivateKey) keyFactory.generatePrivate(spec);
    }

    private RSAPublicKey derivePublicFromPrivate(final RSAPrivateKey priv)
            throws Exception {
        if (priv instanceof RSAPrivateCrtKey) {
            final RSAPrivateCrtKey crt = (RSAPrivateCrtKey) priv;
            final RSAPublicKeySpec pubSpec = new RSAPublicKeySpec(
                crt.getModulus(),
                crt.getPublicExponent()
            );
            return (RSAPublicKey) KeyFactory.getInstance("RSA")
                .generatePublic(pubSpec);
        }
        throw new IllegalArgumentException(
            "Private key is not RSA CRT key; cannot derive public exponent"
        );
    }

    private String computeKid(final RSAPublicKey pub) throws Exception {
        final byte[] sha = MessageDigest.getInstance("SHA-256")
            .digest(pub.getEncoded());
        return Base64.getUrlEncoder().withoutPadding().encodeToString(sha);
    }

    @Override
    public Optional<RSAPrivateKey> getPrivateKey() {
        return Optional.ofNullable(privateKey);
    }

    @Override
    public Optional<RSAPublicKey> getPublicKeyByKid(final String keyId) {
        return Optional.ofNullable(publicCache.get(keyId));
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
