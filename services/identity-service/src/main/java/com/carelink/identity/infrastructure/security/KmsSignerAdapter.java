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

public class KmsSignerAdapter implements JwtKeyProvider {
    private static final Logger log = LoggerFactory.getLogger(KmsSignerAdapter.class);
    private final String signingUrl;
    private final String keyId;
    private final Map<String, RSAPublicKey> publicCache = new ConcurrentHashMap<>();

    public KmsSignerAdapter(String signingUrl, String keyId, String jwksUrl) {
        this.signingUrl = signingUrl;
        this.keyId = keyId;
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
    }

    @Override
    public Optional<java.security.interfaces.RSAPrivateKey> getPrivateKey() {
        return Optional.empty();
    }

    @Override
    public Optional<RSAPublicKey> getPublicKeyByKid(String kid) {
        return Optional.ofNullable(publicCache.get(kid));
    }

    @Override
    public Optional<String> getDefaultKid() {
        return Optional.ofNullable(keyId);
    }

    @Override
    public Optional<byte[]> sign(byte[] signingInput, String kid) {
        if (keyId != null && kid != null && !keyId.equals(kid)) {
            return Optional.empty();
        }
        if (signingUrl == null || signingUrl.isBlank()) {
            log.warn("KMS signing URL not configured");
            return Optional.empty();
        }
        try {
            URL url = new URL(signingUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(Integer.parseInt(System.getenv().getOrDefault("JWT_KMS_CONNECT_TIMEOUT_MS", "5000")));
            conn.setReadTimeout(Integer.parseInt(System.getenv().getOrDefault("JWT_KMS_READ_TIMEOUT_MS", "5000")));
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json");

            String payload = "{\"key_id\":\"" + (keyId == null ? "" : keyId) + "\",\"signing_input\":\""
                    + Base64.getUrlEncoder().withoutPadding().encodeToString(signingInput) + "\"}";

            try (OutputStream os = conn.getOutputStream()) {
                os.write(payload.getBytes(StandardCharsets.UTF_8));
            }
            int code = conn.getResponseCode();
            if (code != 200) {
                log.warn("KMS signer returned HTTP {}", code);
                return Optional.empty();
            }
            byte[] bodyBytes = conn.getInputStream().readAllBytes();
            String body = new String(bodyBytes, StandardCharsets.UTF_8);
            String sigB64 = parseSignatureFromJson(body);
            if (sigB64 == null) {
                log.warn("KMS signer response missing signature");
                return Optional.empty();
            }
            byte[] sigBytes = Base64.getUrlDecoder().decode(sigB64);
            return Optional.of(sigBytes);
        } catch (Exception e) {
            log.error("KMS signing failed: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private String parseSignatureFromJson(String body) {
        if (body == null) return null;
        String key = "\"signature\"";
        int idx = body.indexOf(key);
        if (idx == -1) return null;
        int colon = body.indexOf(':', idx + key.length());
        if (colon == -1) return null;
        int firstQuote = body.indexOf('"', colon);
        if (firstQuote == -1) return null;
        int secondQuote = body.indexOf('"', firstQuote + 1);
        if (secondQuote == -1) return null;
        return body.substring(firstQuote + 1, secondQuote);
    }
}
