package com.carelink.identity.integration;

import com.carelink.identity.infrastructure.security.JwksKeyProvider;
import com.carelink.identity.infrastructure.security.StaticKeyProvider;
import com.carelink.identity.infrastructure.security.JwtService;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class JwksAndRefreshIntegrationTest {

    @Test
    void jwksProviderAndJwtVerification() throws Exception {
        RSAKey rsa1 = new RSAKeyGenerator(2048).keyID(UUID.randomUUID().toString()).generate();
        RSAKey rsa2 = new RSAKeyGenerator(2048).keyID(UUID.randomUUID().toString()).generate();
        JWKSet set = new JWKSet(java.util.List.of(rsa1.toPublicJWK(), rsa2.toPublicJWK()));

        File tmp = File.createTempFile("jwks", ".json");
        Files.writeString(tmp.toPath(), new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(set.toJSONObject()), StandardCharsets.UTF_8);

        JwksKeyProvider provider = new JwksKeyProvider(tmp.toURI().toURL().toString(), 10);
        JwtService jwtService = new JwtService(provider);

        // sign token with rsa1 private key
        JWSSigner signer = new RSASSASigner(rsa1.toRSAPrivateKey());
        Date now = new Date();
        Date exp = Date.from(java.time.Instant.now().plus(5, ChronoUnit.MINUTES));
        JWTClaimsSet claims = new JWTClaimsSet.Builder().subject("sub-1").issuer("carelink-identity").issueTime(now).expirationTime(exp).build();
        SignedJWT jwt = new SignedJWT(new com.nimbusds.jose.JWSHeader.Builder(com.nimbusds.jose.JWSAlgorithm.RS256).keyID(rsa1.getKeyID()).build(), claims);
        jwt.sign(signer);
        String token = jwt.serialize();

        var parsed = jwtService.parseAndValidate(token);
        assertEquals("sub-1", parsed.getSubject());
    }

    @Test
    void loginAndRefreshRotatesRefreshToken() throws Exception {
        // in-memory implementations
        class InMemoryUserRepo implements com.carelink.identity.domain.port.UserRepository {
            private java.util.Map<String, com.carelink.identity.domain.User> map = new java.util.HashMap<>();
            @Override public java.util.Optional<com.carelink.identity.domain.User> findByEmail(String email) { return java.util.Optional.ofNullable(map.get(email)); }
            @Override public java.util.Optional<com.carelink.identity.domain.User> findById(java.util.UUID id) { return map.values().stream().filter(u -> u.id().equals(id)).findFirst(); }
            @Override public void save(com.carelink.identity.domain.User user) { map.put(user.email().value(), user); }
        }

        class InMemorySessionRepo implements com.carelink.identity.domain.port.SessionRepository {
            public java.util.Map<UUID, com.carelink.identity.domain.Session> map = new java.util.HashMap<>();
            @Override
            public java.util.Optional<com.carelink.identity.domain.Session> findByRefreshToken(String token) {
                return map.values().stream().filter(s -> s.refreshToken().equals(token)).findFirst();
            }

            @Override
            public void save(com.carelink.identity.domain.Session session) {
                map.put(session.id(), session);
            }

            @Override
            public void deleteById(UUID id) { map.remove(id); }

            @Override
            public java.util.List<com.carelink.identity.domain.Session> findByUserId(UUID userId) {
                return map.values().stream().filter(s -> s.userId().equals(userId)).sorted(java.util.Comparator.comparing(com.carelink.identity.domain.Session::createdAt)).collect(java.util.stream.Collectors.toList());
            }
        }

        class InMemoryTenantRepo implements com.carelink.identity.domain.port.TenantRepository { @Override public java.util.Optional<com.carelink.identity.domain.Tenant> findBySlug(String slug) { return java.util.Optional.empty(); } @Override public void save(com.carelink.identity.domain.Tenant tenant) {} }

        class NoopSchemaProvisioner implements com.carelink.identity.domain.port.SchemaProvisioner { @Override public void provisionSchema(String tenantSlug) {} }

        class NoopEmailNotifier implements com.carelink.identity.domain.port.EmailNotifier { @Override public void sendVerificationEmail(String to, String token) {} }

        class InMemoryVerificationTokenRepo implements com.carelink.identity.domain.port.VerificationTokenRepository {
            private java.util.Map<String, java.util.UUID> map = new java.util.HashMap<>();
            @Override public void save(String token, java.util.UUID userId) { map.put(token, userId); }
            @Override public java.util.Optional<java.util.UUID> findUserIdByToken(String token) { return java.util.Optional.ofNullable(map.get(token)); }
            @Override public void delete(String token) { map.remove(token); }
        }

        class InMemoryPasswordEncoder implements com.carelink.identity.domain.port.PasswordEncoder {
            @Override public String encode(CharSequence rawPassword) { return "enc:" + rawPassword; }
            @Override public boolean matches(CharSequence rawPassword, String encodedPassword) { return encodedPassword.equals("enc:" + rawPassword); }
        }

        InMemoryUserRepo userRepo = new InMemoryUserRepo();
        InMemorySessionRepo sessionRepo = new InMemorySessionRepo();
        InMemoryPasswordEncoder pwd = new InMemoryPasswordEncoder();

        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        com.carelink.identity.domain.User u = new com.carelink.identity.domain.User(userId, tenantId, new com.carelink.identity.domain.value.Email("u@example.com"), "TENANT_ADMIN", new com.carelink.identity.domain.value.HashedPassword(pwd.encode("secret")), OffsetDateTime.now());
        userRepo.save(u);

        StaticKeyProvider staticKeyProvider = new StaticKeyProvider();
        JwtService jwtService = new JwtService(staticKeyProvider);

        com.carelink.identity.infrastructure.web.AuthController controller = new com.carelink.identity.infrastructure.web.AuthController(
                new InMemoryTenantRepo(),
                userRepo,
                new NoopSchemaProvisioner(),
                new NoopEmailNotifier(),
                pwd,
                new InMemoryVerificationTokenRepo(),
            sessionRepo
        );
        controller.setJwtService(jwtService);

        MockMvc mvc = MockMvcBuilders.standaloneSetup(controller).build();

        String loginJson = "{\"email\":\"u@example.com\",\"password\":\"secret\"}";
        MvcResult loginRes = mvc.perform(post("/api/v1/auth/login").contentType(org.springframework.http.MediaType.APPLICATION_JSON).content(loginJson))
                .andExpect(status().isOk())
                .andReturn();

        var respCookie = loginRes.getResponse().getCookie("refresh_token");
        assertNotNull(respCookie);
        String origToken = respCookie.getValue();
        assertNotNull(origToken);

        // call refresh using cookie (MockMvc will populate request.getCookies())
        jakarta.servlet.http.Cookie origCookie = new jakarta.servlet.http.Cookie("refresh_token", origToken);
        MvcResult refreshRes = mvc.perform(post("/api/v1/auth/refresh").cookie(origCookie))
            .andExpect(status().isOk())
            .andReturn();

        var newRespCookie = refreshRes.getResponse().getCookie("refresh_token");
        assertNotNull(newRespCookie);
        String newToken = newRespCookie.getValue();
        assertNotNull(newToken);
        assertNotEquals(origToken, newToken);

        // old token should no longer be valid — standalone MockMvc throws the exception, assert it is thrown
        jakarta.servlet.http.Cookie oldCookie = new jakarta.servlet.http.Cookie("refresh_token", origToken);
        Exception ex = assertThrows(Exception.class, () -> mvc.perform(post("/api/v1/auth/refresh").cookie(oldCookie)).andReturn());
        assertTrue(ex.getMessage().contains("Invalid refresh token") || (ex.getCause() != null && ex.getCause().getMessage().contains("Invalid refresh token")));
    }
}
