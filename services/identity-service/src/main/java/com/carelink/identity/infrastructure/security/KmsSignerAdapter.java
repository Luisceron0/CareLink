package com.carelink.identity.infrastructure.security;

import com.carelink.identity.domain.port.JwtKeyProvider;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class KmsSignerAdapter implements JwtKeyProvider {

    /** Class logger. */
    private static final Logger LOG = LoggerFactory.getLogger(
        KmsSignerAdapter.class
    );

    /** Successful HTTP status code. */
    private static final int HTTP_OK = 200;

    /** Remote signing endpoint. */
    private final String signingUrl;

    /** Current signing key identifier. */
    private final String keyId;

    /** Cached public keys from JWKS endpoint. */
    private final Map<String, RSAPublicKey> publicCache =
        new ConcurrentHashMap<>();

    /**
     * Builds a sign-only KMS adapter.
     *
     * @param signingUrlValue kms signing url
     * @param keyIdValue key identifier
     * @param jwksUrl jwks endpoint for public keys
     */
    public KmsSignerAdapter(
            final String signingUrlValue,
            final String keyIdValue,
            final String jwksUrl) {
        this.signingUrl = signingUrlValue;
        this.keyId = keyIdValue;
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
    }

    @Override
    public Optional<java.security.interfaces.RSAPrivateKey> getPrivateKey() {
        return Optional.empty();
    }

    @Override
    public Optional<RSAPublicKey> getPublicKeyByKid(final String kid) {
        return Optional.ofNullable(publicCache.get(kid));
    }

    @Override
    public Optional<String> getDefaultKid() {
        return Optional.ofNullable(keyId);
    }

    @Override
    public Optional<byte[]> sign(final byte[] signingInput, final String kid) {
        if (keyId != null && kid != null && !keyId.equals(kid)) {
            return Optional.empty();
        }
        if (signingUrl == null || signingUrl.isBlank()) {
            LOG.warn("KMS signing URL not configured");
            return Optional.empty();
        }
        try {
            final URL url = new URL(signingUrl);
            final HttpURLConnection conn =
                (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(Integer.parseInt(
                System.getenv().getOrDefault(
                    "JWT_KMS_CONNECT_TIMEOUT_MS",
                    "5000"
                )
            ));
            conn.setReadTimeout(Integer.parseInt(System.getenv().getOrDefault(
                "JWT_KMS_READ_TIMEOUT_MS",
                "5000"
            )));
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json");

            final String payload = "{\"key_id\":\""
                + (keyId == null ? "" : keyId)
                + "\",\"signing_input\":\""
                + Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(signingInput)
                + "\"}";

            try (OutputStream os = conn.getOutputStream()) {
                os.write(payload.getBytes(StandardCharsets.UTF_8));
            }
            final int code = conn.getResponseCode();
            if (code != HTTP_OK) {
                LOG.warn("KMS signer returned HTTP {}", code);
                return Optional.empty();
            }
            final byte[] bodyBytes = conn.getInputStream().readAllBytes();
            final String body = new String(bodyBytes, StandardCharsets.UTF_8);
            final String sigB64 = parseSignatureFromJson(body);
            if (sigB64 == null) {
                LOG.warn("KMS signer response missing signature");
                return Optional.empty();
            }
            final byte[] sigBytes = Base64.getUrlDecoder().decode(sigB64);
            return Optional.of(sigBytes);
        } catch (Exception e) {
            LOG.error("KMS signing failed: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private String parseSignatureFromJson(final String body) {
        if (body == null) {
            return null;
        }
        final String key = "\"signature\"";
        final int idx = body.indexOf(key);
        if (idx == -1) {
            return null;
        }
        final int colon = body.indexOf(':', idx + key.length());
        if (colon == -1) {
            return null;
        }
        final int firstQuote = body.indexOf('"', colon);
        if (firstQuote == -1) {
            return null;
        }
        final int secondQuote = body.indexOf('"', firstQuote + 1);
        if (secondQuote == -1) {
            return null;
        }
        return body.substring(firstQuote + 1, secondQuote);
    }
}
