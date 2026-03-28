package com.carelink.identity.infrastructure.security;

import com.carelink.identity.domain.port.JwtKeyProvider;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;
import java.security.interfaces.RSAPublicKey;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class JwksKeyProvider implements JwtKeyProvider {
    private static final Logger log = LoggerFactory.getLogger(JwksKeyProvider.class);
    private final String jwksUrl;
    private final Map<String, RSAPublicKey> cache = new ConcurrentHashMap<>();
    private volatile long lastFetch = 0L;
    private final long ttlMs;

    public JwksKeyProvider(String jwksUrl, long ttlSeconds) {
        this.jwksUrl = jwksUrl;
        this.ttlMs = ttlSeconds * 1000L;
        try {
            if (jwksUrl != null && !jwksUrl.isBlank()) {
                java.net.URL url = new java.net.URL(jwksUrl);
                if (!"https".equalsIgnoreCase(url.getProtocol())) {
                    log.warn("JWKS URL must use HTTPS: {}", jwksUrl);
                } else {
                    String allow = System.getenv().getOrDefault("JWT_JWKS_HOST_ALLOWLIST", "");
                    if (!allow.isBlank()) {
                        var allowed = java.util.Arrays.stream(allow.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
                        if (!allowed.contains(url.getHost())) {
                            log.warn("JWKS host {} not in allowlist, skipping preload", url.getHost());
                        } else {
                            fetch();
                        }
                    } else {
                        fetch();
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Unable to preload JWKS from {}: {}", jwksUrl, e.getMessage());
        }
    }

    private synchronized void fetch() {
        try {
            if (jwksUrl == null) return;
            if (System.currentTimeMillis() - lastFetch < ttlMs) return;
            java.net.URL url = new java.net.URL(jwksUrl);
            java.net.URLConnection conn = url.openConnection();
            conn.setConnectTimeout(Integer.parseInt(System.getenv().getOrDefault("JWT_JWKS_CONNECT_TIMEOUT_MS", "5000")));
            conn.setReadTimeout(Integer.parseInt(System.getenv().getOrDefault("JWT_JWKS_READ_TIMEOUT_MS", "5000")));
            try (java.io.InputStream in = conn.getInputStream()) {
                JWKSet set = JWKSet.load(in);
                for (JWK jwk : set.getKeys()) {
                    if (jwk instanceof RSAKey) {
                        RSAKey r = (RSAKey) jwk;
                        cache.put(r.getKeyID(), r.toRSAPublicKey());
                    }
                }
                lastFetch = System.currentTimeMillis();
            }
        } catch (Exception e) {
            log.warn("Failed to fetch JWKS: {}", e.getMessage());
        }
    }

    @Override
    public Optional<java.security.interfaces.RSAPrivateKey> getPrivateKey() {
        return Optional.empty();
    }

    @Override
    public Optional<RSAPublicKey> getPublicKeyByKid(String kid) {
        try { fetch(); } catch (Exception ignored) {}
        return Optional.ofNullable(cache.get(kid));
    }

    @Override
    public Optional<String> getDefaultKid() { return Optional.empty(); }

    @Override
    public Optional<byte[]> sign(byte[] signingInput, String kid) {
        // JWKS provider holds only public keys; cannot sign.
        return Optional.empty();
    }
}
