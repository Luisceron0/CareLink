package com.carelink.identity.infrastructure.security;

import com.carelink.identity.domain.port.JwtKeyProvider;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.nimbusds.jose.util.Base64URL;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import org.springframework.stereotype.Component;

import java.security.Signature;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

@Component
public final class JwtService {

    /** Milliseconds in one second. */
    private static final long MILLIS_PER_SECOND = 1000L;

    /** JWT issuer claim value. */
    private static final String ISSUER = "carelink-identity";

    /** JWT signing and verification key provider. */
    private final JwtKeyProvider keyProvider;

    /** Access token TTL in seconds. */
    private final long accessTokenTtlSeconds;

    /**
     * Builds JWT service using environment-based TTL.
     *
     * @param keyProviderValue key provider
     */
    public JwtService(final JwtKeyProvider keyProviderValue) {
        this.keyProvider = keyProviderValue;
        final String ttl = System.getenv().getOrDefault(
            "JWT_ACCESS_TTL",
            "900"
        );
        this.accessTokenTtlSeconds = Long.parseLong(ttl);
    }

    /**
     * Generates a signed RS256 access token.
     *
     * @param userId subject user identifier
     * @param tenantId tenant identifier
     * @param role user role
     * @return signed JWT
     */
    public String generateAccessToken(
            final UUID userId,
            final UUID tenantId,
            final String role) {
        try {
            final String keyId = keyProvider.getDefaultKid().orElse(null);

            final Date now = new Date();
            final Date exp = new Date(
                now.getTime() + accessTokenTtlSeconds * MILLIS_PER_SECOND
            );

            final JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder()
                    .subject(userId.toString())
                    .issuer(ISSUER)
                    .issueTime(now)
                    .expirationTime(exp)
                    .claim("role", role);

            if (tenantId != null) {
                claims.claim("tenant_id", tenantId.toString());
            }

            final JWSHeader.Builder headerBuilder = new JWSHeader.Builder(
                JWSAlgorithm.RS256
            ).type(JOSEObjectType.JWT);
            if (keyId != null) {
                headerBuilder.keyID(keyId);
            }
            final JWSHeader header = headerBuilder.build();

            final SignedJWT unsigned = new SignedJWT(header, claims.build());
            final String headerJson = unsigned.getHeader()
                .toJSONObject()
                .toString();
            final String payloadJson =
                unsigned.getJWTClaimsSet().toJSONObject().toString();
            final String headerB64 = Base64URL.encode(
                headerJson.getBytes(StandardCharsets.UTF_8)
            ).toString();
            final String payloadB64 = Base64URL.encode(
                payloadJson.getBytes(StandardCharsets.UTF_8)
            ).toString();
            final String signingInput = headerB64 + "." + payloadB64;

            final Optional<byte[]> remoteSig = keyProvider.sign(
                signingInput.getBytes(StandardCharsets.US_ASCII),
                keyId
            );
            byte[] signatureBytes;
            if (remoteSig.isPresent()) {
                signatureBytes = remoteSig.get();
            } else {
                final RSAPrivateKey signingKey =
                    keyProvider.getPrivateKey().orElseThrow(
                        () -> new RuntimeException(
                            "No private key available for signing"
                        )
                    );
                final Signature sig = Signature.getInstance("SHA256withRSA");
                sig.initSign(signingKey);
                sig.update(signingInput.getBytes(StandardCharsets.US_ASCII));
                signatureBytes = sig.sign();
            }

            final String signatureB64 = Base64URL.encode(signatureBytes)
                .toString();
            return signingInput + "." + signatureB64;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Parses and validates a JWT token.
     *
     * @param token compact JWT string
     * @return validated claims
     */
    public JWTClaimsSet parseAndValidate(final String token) {
        try {
            final SignedJWT signedJWT = SignedJWT.parse(token);
            final String keyId = signedJWT.getHeader().getKeyID();
            final RSAPublicKey publicKey = keyProvider.getPublicKeyByKid(keyId)
                .orElseThrow(() -> new RuntimeException(
                    "No public key found for kid=" + keyId
                ));
            final RSASSAVerifier verifier = new RSASSAVerifier(publicKey);
            if (!signedJWT.verify(verifier)) {
                throw new RuntimeException("Invalid JWT signature");
            }
            final JWTClaimsSet claims = signedJWT.getJWTClaimsSet();
            final Date exp = claims.getExpirationTime();
            if (exp == null || new Date().after(exp)) {
                throw new RuntimeException("Token expired");
            }
            return claims;
        } catch (Exception e) {
            throw new RuntimeException("Invalid token", e);
        }
    }
}
