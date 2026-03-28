package com.carelink.identity.infrastructure.web;

import com.carelink.identity.application.dto.AuthResponse;
import com.carelink.identity.application.dto.LoginRequest;
import com.carelink.identity.application.dto.RegisterTenantRequest;
import com.carelink.identity.application.dto.RefreshRequest;
import com.carelink.identity.application.usecase.LoginUseCase;
import com.carelink.identity.application.usecase.RegisterTenantUseCase;
import com.carelink.identity.application.usecase.VerifyEmailUseCase;
import com.carelink.identity.application.usecase.RefreshTokenUseCase;
import com.carelink.identity.application.usecase.LogoutUseCase;
import com.carelink.identity.domain.Tenant;
import com.carelink.identity.domain.User;
import com.carelink.identity.domain.Session;
import com.carelink.identity.domain.port.*;
import com.carelink.identity.infrastructure.security.JwtService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final RegisterTenantUseCase registerTenantUseCase;
    private final LoginUseCase loginUseCase;
    private final VerifyEmailUseCase verifyEmailUseCase;
    private final RefreshTokenUseCase refreshTokenUseCase;
    private final LogoutUseCase logoutUseCase;
    private final JwtService jwtService;
    private final UserRepository userRepository;

    public AuthController(TenantRepository tenantRepository,
                          UserRepository userRepository,
                          SchemaProvisioner schemaProvisioner,
                          EmailNotifier emailNotifier,
                          PasswordEncoder passwordEncoder,
                          VerificationTokenRepository tokenRepository,
                          SessionRepository sessionRepository,
                          JwtService jwtService) {
        this.registerTenantUseCase = new RegisterTenantUseCase(tenantRepository, userRepository, schemaProvisioner, emailNotifier, passwordEncoder, tokenRepository);
        this.loginUseCase = new LoginUseCase(userRepository, passwordEncoder, sessionRepository);
        this.verifyEmailUseCase = new VerifyEmailUseCase(tokenRepository);
        this.refreshTokenUseCase = new RefreshTokenUseCase(session_repository(sessionRepository));
        this.logoutUseCase = new LogoutUseCase(session_repository(sessionRepository));
        this.jwtService = jwtService;
        this.userRepository = userRepository;
    }

    // helper to satisfy single-use creation while keeping code explicit
    private static SessionRepository session_repository(SessionRepository s) { return s; }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegisterTenantRequest req) {
        Tenant tenant = registerTenantUseCase.execute(req.getName(), req.getSlug(), req.getTaxId(), req.getAdminEmail(), req.getPassword());
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("tenantId", tenant.id().toString(), "slug", tenant.slug().value()));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@RequestBody LoginRequest req, HttpServletResponse response) {
        Session session = loginUseCase.execute(req.getEmail(), req.getPassword());
        User user = userRepository.findByEmail(req.getEmail()).orElseThrow(() -> new RuntimeException("User not found after login"));
        String access = jwtService.generateAccessToken(user.id(), user.tenantId(), user.role());

        long maxAge = Long.parseLong(System.getenv().getOrDefault("REFRESH_TOKEN_TTL_SECONDS", String.valueOf(7 * 24 * 3600)));
        ResponseCookie cookie = ResponseCookie.from("refresh_token", session.refreshToken())
                .httpOnly(true)
                .secure(true)
                .path("/")
                .sameSite("Strict")
                .maxAge(maxAge)
                .build();

        return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, cookie.toString()).body(new AuthResponse(access));
    }

    @PostMapping("/verify")
    public ResponseEntity<?> verify(@RequestParam("token") String token) {
        user_id_check(verifyEmailUseCase.execute(token));
        return ResponseEntity.ok(Map.of("verified", true));
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(@RequestBody(required = false) RefreshRequest req, HttpServletRequest request, HttpServletResponse response) {
        String token = null;
        if (req != null && req.getRefreshToken() != null && !req.getRefreshToken().isBlank()) token = req.getRefreshToken();
        if (token == null) {
            Cookie[] cookies = request.getCookies();
            if (cookies != null) {
                for (Cookie c : cookies) {
                    if ("refresh_token".equals(c.getName())) { token = c.getValue(); break; }
                }
            }
        }
        if (token == null) throw new RuntimeException("No refresh token provided");

        Session session = refreshTokenUseCase.execute(token);
        User user = userRepository.findById(session.userId()).orElseThrow(() -> new RuntimeException("User not found for session"));
        String access = jwtService.generateAccessToken(user.id(), user.tenantId(), user.role());

        long maxAge = Long.parseLong(System.getenv().getOrDefault("REFRESH_TOKEN_TTL_SECONDS", String.valueOf(7 * 24 * 3600)));
        ResponseCookie cookie = ResponseCookie.from("refresh_token", session.refreshToken())
                .httpOnly(true)
                .secure(true)
                .path("/")
                .sameSite("Strict")
                .maxAge(maxAge)
                .build();

        return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, cookie.toString()).body(new AuthResponse(access));
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(@RequestBody(required = false) RefreshRequest req, HttpServletRequest request) {
        String token = null;
        if (req != null && req.getRefreshToken() != null && !req.getRefreshToken().isBlank()) token = req.getRefreshToken();
        if (token == null) {
            Cookie[] cookies = request.getCookies();
            if (cookies != null) {
                for (Cookie c : cookies) {
                    if ("refresh_token".equals(c.getName())) { token = c.getValue(); break; }
                }
            }
        }
        if (token != null) logoutUseCase.execute(token);

        ResponseCookie expired = ResponseCookie.from("refresh_token", "").httpOnly(true).secure(true).path("/").maxAge(0).build();
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, expired.toString()).build();
    }

    private void user_id_check(java.util.UUID id) {
        // placeholder to record use; domain-level audit will handle persistence if needed
    }
}
