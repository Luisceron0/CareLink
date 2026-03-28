package com.carelink.identity.integration;

import com.carelink.identity.infrastructure.web.AuthController;
import com.carelink.identity.infrastructure.web.ProtectedController;
import com.carelink.identity.infrastructure.security.JwtService;
import com.carelink.identity.infrastructure.security.SecurityConfig;
import com.carelink.identity.domain.User;
import com.carelink.identity.domain.value.Email;
import com.carelink.identity.domain.value.HashedPassword;
import com.carelink.identity.domain.port.*;
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

@WebMvcTest(controllers = {AuthController.class, ProtectedController.class})
@Import({SecurityConfig.class, JwtService.class, com.carelink.identity.infrastructure.security.StaticKeyProvider.class})
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

    @Test
    void loginGeneratesTokenAndSecuresEndpoint() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        User user = new User(userId, tenantId, new Email("u@example.com"), "TENANT_ADMIN", new HashedPassword("enc:secret"), OffsetDateTime.now());

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
}
