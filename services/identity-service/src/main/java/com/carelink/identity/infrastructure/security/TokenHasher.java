package com.carelink.identity.infrastructure.security;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

public final class TokenHasher {
    private static final String HMAC_ALGO = "HmacSHA256";
    private static final String secret = System.getenv().getOrDefault("REFRESH_TOKEN_HMAC_SECRET", "dev-refresh-secret");

    private TokenHasher() {}

    public static String hash(String token) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGO);
            mac.init(new SecretKeySpec(secret.getBytes(), HMAC_ALGO));
            byte[] h = mac.doFinal(token.getBytes());
            return Base64.getUrlEncoder().withoutPadding().encodeToString(h);
        } catch (Exception e) {
            throw new RuntimeException("Unable to hash token", e);
        }
    }
}
