package com.carelink.identity.infrastructure.security;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

public final class TokenHasher {

    /** HMAC algorithm name. */
    private static final String HMAC_ALGO = "HmacSHA256";

    /** Secret used to hash refresh tokens. */
    private static final String SECRET = System.getenv().getOrDefault(
        "REFRESH_TOKEN_HMAC_SECRET",
        "dev-refresh-secret"
    );

    private TokenHasher() {
    }

    /**
     * Hashes a refresh token with HMAC-SHA256.
     *
     * @param token raw token value
     * @return URL-safe hash
     */
    public static String hash(final String token) {
        try {
            final Mac mac = Mac.getInstance(HMAC_ALGO);
            mac.init(new SecretKeySpec(SECRET.getBytes(), HMAC_ALGO));
            final byte[] hash = mac.doFinal(token.getBytes());
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (Exception e) {
            throw new RuntimeException("Unable to hash token", e);
        }
    }
}
