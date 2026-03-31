package com.carelink.identity.infrastructure.security;

import com.carelink.identity.domain.port.JwtKeyProvider;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.interfaces.RSAPublicKey;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class JwksKeyProvider implements JwtKeyProvider {

    /** Class logger. */
    private static final Logger LOG = LoggerFactory.getLogger(
        JwksKeyProvider.class
    );

    /** Milliseconds in one second. */
    private static final long MILLIS_PER_SECOND = 1000L;

    /** Remote JWKS URL. */
    private final String jwksUrl;

    /** In-memory public key cache by kid. */
    private final Map<String, RSAPublicKey> cache = new ConcurrentHashMap<>();

    /** Last successful fetch timestamp. */
    private volatile long lastFetch = 0L;

    /** Cache TTL in milliseconds. */
    private final long ttlMs;

    /**
     * Builds a JWKS-backed key provider.
     *
     * @param jwksUrlValue jwks endpoint
     * @param ttlSeconds cache ttl in seconds
     */
    public JwksKeyProvider(final String jwksUrlValue, final long ttlSeconds) {
        this.jwksUrl = jwksUrlValue;
        this.ttlMs = ttlSeconds * MILLIS_PER_SECOND;
        try {
            if (jwksUrl != null && !jwksUrl.isBlank()) {
                final java.net.URL url = new java.net.URL(jwksUrl);
                if (!"https".equalsIgnoreCase(url.getProtocol())) {
                    LOG.warn("JWKS URL must use HTTPS: {}", jwksUrl);
                } else {
                    final String allow = System.getenv().getOrDefault(
                        "JWT_JWKS_HOST_ALLOWLIST",
                        ""
                    );
                    if (!allow.isBlank()) {
                        final var allowed = java.util.Arrays.stream(
                            allow.split(",")
                        ).map(String::trim).filter(s -> !s.isEmpty()).toList();
                        if (!allowed.contains(url.getHost())) {
                            LOG.warn(
                                "JWKS host {} not in allowlist, "
                                    + "skipping preload",
                                url.getHost()
                            );
                        } else {
                            fetch();
                        }
                    } else {
                        fetch();
                    }
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

    private synchronized void fetch() {
        try {
            if (jwksUrl == null) {
                return;
            }
            if (System.currentTimeMillis() - lastFetch < ttlMs) {
                return;
            }
            final java.net.URL url = new java.net.URL(jwksUrl);
            final java.net.URLConnection conn = url.openConnection();
            conn.setConnectTimeout(Integer.parseInt(
                System.getenv().getOrDefault(
                    "JWT_JWKS_CONNECT_TIMEOUT_MS",
                    "5000"
                )
            ));
            conn.setReadTimeout(Integer.parseInt(System.getenv().getOrDefault(
                "JWT_JWKS_READ_TIMEOUT_MS",
                "5000"
            )));
            try (java.io.InputStream in = conn.getInputStream()) {
                final JWKSet set = JWKSet.load(in);
                for (JWK jwk : set.getKeys()) {
                    if (jwk instanceof RSAKey) {
                        final RSAKey rsaKey = (RSAKey) jwk;
                        cache.put(rsaKey.getKeyID(), rsaKey.toRSAPublicKey());
                    }
                }
                lastFetch = System.currentTimeMillis();
            }
        } catch (Exception e) {
            LOG.warn("Failed to fetch JWKS: {}", e.getMessage());
        }
    }

    @Override
    public Optional<java.security.interfaces.RSAPrivateKey> getPrivateKey() {
        return Optional.empty();
    }

    @Override
    public Optional<RSAPublicKey> getPublicKeyByKid(final String kid) {
        try {
            fetch();
        } catch (RuntimeException ignored) {
            // Best-effort refresh only.
        }
        return Optional.ofNullable(cache.get(kid));
    }

    @Override
    public Optional<String> getDefaultKid() {
        return Optional.empty();
    }

    @Override
    public Optional<byte[]> sign(
            final byte[] signingInput,
            final String kid) {
        // JWKS provider holds only public keys; cannot sign.
        return Optional.empty();
    }
}
