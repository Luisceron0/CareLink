package com.carelink.identity.infrastructure.security;

import com.carelink.identity.domain.port.JwtKeyProvider;
import com.nimbusds.jose.*;
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
public class JwtService {
    private final JwtKeyProvider keyProvider;
    private final long accessTokenTtlSeconds;

    public JwtService(JwtKeyProvider keyProvider) {
        this.keyProvider = keyProvider;
        String ttl = System.getenv().getOrDefault("JWT_ACCESS_TTL", "900");
        this.accessTokenTtlSeconds = Long.parseLong(ttl);
    }

    public String generateAccessToken(UUID userId, UUID tenantId, String role) {
        try {
            String kid = keyProvider.getDefaultKid().orElse(null);

            Date now = new Date();
            Date exp = new Date(now.getTime() + accessTokenTtlSeconds * 1000);

            JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder()
                    .subject(userId.toString())
                    .issuer("carelink-identity")
                    .issueTime(now)
                    .expirationTime(exp)
                    .claim("role", role);

            if (tenantId != null) claims.claim("tenant_id", tenantId.toString());

            JWSHeader.Builder headerBuilder = new JWSHeader.Builder(JWSAlgorithm.RS256).type(JOSEObjectType.JWT);
            if (kid != null) headerBuilder.keyID(kid);
            JWSHeader header = headerBuilder.build();

            // Build signing input (base64url(header) + "." + base64url(payload))
            SignedJWT unsigned = new SignedJWT(header, claims.build());
            String headerJson = unsigned.getHeader().toJSONObject().toString();
            String payloadJson = unsigned.getJWTClaimsSet().toJSONObject().toString();
            String headerB64 = Base64URL.encode(headerJson.getBytes(StandardCharsets.UTF_8)).toString();
            String payloadB64 = Base64URL.encode(payloadJson.getBytes(StandardCharsets.UTF_8)).toString();
            String signingInput = headerB64 + "." + payloadB64;

            // Prefer provider-side signing (KMS) if available
            Optional<byte[]> remoteSig = keyProvider.sign(signingInput.getBytes(StandardCharsets.US_ASCII), kid);
            byte[] signatureBytes;
            if (remoteSig.isPresent()) {
                signatureBytes = remoteSig.get();
            } else {
                // Fallback: sign locally with private key
                RSAPrivateKey signingKey = keyProvider.getPrivateKey().orElseThrow(() -> new RuntimeException("No private key available for signing"));
                Signature sig = Signature.getInstance("SHA256withRSA");
                sig.initSign(signingKey);
                sig.update(signingInput.getBytes(StandardCharsets.US_ASCII));
                signatureBytes = sig.sign();
            }

            String signatureB64 = Base64URL.encode(signatureBytes).toString();
            return signingInput + "." + signatureB64;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public com.nimbusds.jwt.JWTClaimsSet parseAndValidate(String token) {
        try {
            SignedJWT signedJWT = SignedJWT.parse(token);
            String kid = signedJWT.getHeader().getKeyID();
            RSAPublicKey pub = keyProvider.getPublicKeyByKid(kid).orElseThrow(() -> new RuntimeException("No public key found for kid=" + kid));
            RSASSAVerifier verifier = new RSASSAVerifier(pub);
            if (!signedJWT.verify(verifier)) {
                throw new RuntimeException("Invalid JWT signature");
            }
            com.nimbusds.jwt.JWTClaimsSet claims = signedJWT.getJWTClaimsSet();
            Date exp = claims.getExpirationTime();
            if (exp == null || new Date().after(exp)) {
                throw new RuntimeException("Token expired");
            }
            return claims;
        } catch (Exception e) {
            throw new RuntimeException("Invalid token", e);
        }
    }
}
