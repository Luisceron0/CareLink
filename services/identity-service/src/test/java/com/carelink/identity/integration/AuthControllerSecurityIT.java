package com.carelink.identity.integration;

import com.carelink.identity.infrastructure.web.AuthController;
import com.carelink.identity.infrastructure.web.ProtectedController;
import com.carelink.identity.infrastructure.security.JwtService;
import com.carelink.identity.infrastructure.security.LoginRateLimiter;
import com.carelink.identity.infrastructure.security.SecurityConfig;
import com.carelink.identity.domain.User;
import com.carelink.identity.domain.value.Email;
import com.carelink.identity.domain.value.HashedPassword;
import com.carelink.identity.domain.port.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link LoginRateLimiter} se importa REAL, no mockeado — a diferencia del resto de los
 * puertos de este slice. El punto de {@link #fifthConsecutiveFailureLocksOutFurtherAttempts}
 * es verificar el comportamiento real de conteo y lockout tal como lo ejercita el
 * controller, no un doble que finge que existe.
 */
@WebMvcTest(controllers = {AuthController.class, ProtectedController.class})
@Import({SecurityConfig.class, JwtService.class, com.carelink.identity.infrastructure.security.StaticKeyProvider.class,
        LoginRateLimiter.class})
public class AuthControllerSecurityIT {

    @Autowired
    private MockMvc mockMvc;

    @MockBean private TenantRepository tenantRepository;
    @MockBean private UserRepository userRepository;
    @MockBean private SchemaProvisioner schemaProvisioner;
    @MockBean private EmailNotifier emailNotifier;
    @MockBean private PasswordEncoder passwordEncoder;
    @MockBean private VerificationTokenRepository verificationTokenRepository;
    @MockBean private SessionRepository sessionRepository;

    @Autowired
    private LoginRateLimiter loginRateLimiter;

    @BeforeEach
    void resetRateLimiter() {
        // LoginRateLimiter es un singleton con estado mutable y todos los requests de
        // MockMvc comparten la misma IP simulada — sin esto, el orden en que JUnit
        // decide correr los tests de esta clase importaría, y no debería.
        loginRateLimiter.resetForTests();
    }

    @Test
    void loginGeneratesTokenAndSecuresEndpoint() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        User user = new User(userId, tenantId, new Email("u@example.com"), "TENANT_ADMIN", null, true, new HashedPassword("enc:secret"), OffsetDateTime.now());

        when(userRepository.findByEmail("u@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("secret", "enc:secret")).thenReturn(true);

        var loginJson = "{\"email\":\"u@example.com\",\"password\":\"secret\"}";

        var res = mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(loginJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists())
                .andReturn();

        String body = res.getResponse().getContentAsString();
        // Extract accessToken manually
        com.fasterxml.jackson.databind.JsonNode root = new com.fasterxml.jackson.databind.ObjectMapper().readTree(body);
        String access = root.get("accessToken").asText();

        mockMvc.perform(get("/api/v1/test/me").header("Authorization", "Bearer " + access))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sub").value(userId.toString()));
    }

    @Test
    void invalidCredentialsReturn401NotAnUnhandledServerError() throws Exception {
        // Antes de este cambio no había ningún catch alrededor de LoginUseCase: la
        // RuntimeException("Invalid credentials") llegaba sin manejar hasta Spring Boot,
        // que la traducía en un 500 genérico — un fallo de login no es un error del
        // servidor.
        when(userRepository.findByEmail("nadie@example.com")).thenReturn(Optional.empty());

        var loginJson = "{\"email\":\"nadie@example.com\",\"password\":\"lo-que-sea\"}";

        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(loginJson))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void fifthConsecutiveFailureLocksOutFurtherAttempts() throws Exception {
        // FR-ID-03: 5 intentos fallidos → lockout. Todos los requests de MockMvc
        // comparten la misma IP simulada, así que este test ejercita el conteo
        // por-IP sin necesidad de falsificar cabeceras.
        when(userRepository.findByEmail("victima@example.com")).thenReturn(Optional.empty());
        var badLoginJson = "{\"email\":\"victima@example.com\",\"password\":\"incorrecta\"}";

        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(badLoginJson))
                    .andExpect(status().isUnauthorized());
        }

        // El sexto intento ya está bloqueado — incluso si ahora las credenciales
        // fueran correctas, el lockout se verifica antes de tocar LoginUseCase.
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        User user = new User(userId, tenantId, new Email("victima@example.com"), "TENANT_ADMIN", null, true,
                new HashedPassword("enc:correcta"), OffsetDateTime.now());
        when(userRepository.findByEmail("victima@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("correcta", "enc:correcta")).thenReturn(true);
        var goodLoginJson = "{\"email\":\"victima@example.com\",\"password\":\"correcta\"}";

        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(goodLoginJson))
                .andExpect(status().isTooManyRequests());
    }
}
