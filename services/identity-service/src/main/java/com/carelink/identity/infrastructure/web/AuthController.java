package com.carelink.identity.infrastructure.web;

import com.carelink.identity.application.dto.AcceptInvitationRequest;
import com.carelink.identity.application.dto.AuthResponse;
import com.carelink.identity.application.dto.LoginRequest;
import com.carelink.identity.application.dto.RegisterTenantRequest;
import com.carelink.identity.application.dto.RefreshRequest;
import com.carelink.identity.application.usecase.AcceptInvitationUseCase;
import com.carelink.identity.application.usecase.LoginUseCase;
import com.carelink.identity.application.usecase.RegisterTenantUseCase;
import com.carelink.identity.application.usecase.VerifyEmailUseCase;
import com.carelink.identity.application.usecase.RefreshTokenUseCase;
import com.carelink.identity.application.usecase.LogoutUseCase;
import com.carelink.identity.domain.Tenant;
import com.carelink.identity.domain.User;
import com.carelink.identity.domain.Session;
import com.carelink.identity.domain.exception.InvalidInvitationTokenException;
import com.carelink.identity.domain.port.*;
import com.carelink.identity.infrastructure.security.JwtService;
import com.carelink.identity.infrastructure.security.LoginRateLimiter;
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
    private final AcceptInvitationUseCase acceptInvitationUseCase;
    private final RefreshTokenUseCase refreshTokenUseCase;
    private final LogoutUseCase logoutUseCase;
    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final LoginRateLimiter loginRateLimiter;

    public AuthController(TenantRepository tenantRepository,
                          UserRepository userRepository,
                          SchemaProvisioner schemaProvisioner,
                          EmailNotifier emailNotifier,
                          PasswordEncoder passwordEncoder,
                          VerificationTokenRepository tokenRepository,
                          SessionRepository sessionRepository,
                          JwtService jwtService,
                          LoginRateLimiter loginRateLimiter) {
        this.registerTenantUseCase = new RegisterTenantUseCase(tenantRepository, userRepository, schemaProvisioner, emailNotifier, passwordEncoder, tokenRepository);
        this.loginUseCase = new LoginUseCase(userRepository, passwordEncoder, sessionRepository);
        this.verifyEmailUseCase = new VerifyEmailUseCase(tokenRepository);
        this.acceptInvitationUseCase = new AcceptInvitationUseCase(tokenRepository, userRepository, passwordEncoder);
        this.refreshTokenUseCase = new RefreshTokenUseCase(session_repository(sessionRepository));
        this.logoutUseCase = new LogoutUseCase(session_repository(sessionRepository));
        this.jwtService = jwtService;
        this.userRepository = userRepository;
        this.loginRateLimiter = loginRateLimiter;
    }

    // helper to satisfy single-use creation while keeping code explicit
    private static SessionRepository session_repository(SessionRepository s) { return s; }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegisterTenantRequest req) {
        Tenant tenant = registerTenantUseCase.execute(req.getName(), req.getSlug(), req.getTaxId(), req.getAdminEmail(), req.getPassword());
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("tenantId", tenant.id().toString(), "slug", tenant.slug().value()));
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest req, HttpServletRequest request) {
        // request.getRemoteAddr(), no X-Forwarded-For: este milestone no corre detrás de
        // un proxy que sanee ese header (sin demo público, ADR-015 — local y CI
        // solamente), así que confiar en él dejaría a cualquier cliente elegir con qué
        // IP se lo limita, con solo cambiar el valor que manda. §8.4: las cabeceras son
        // input no confiable. getRemoteAddr() es la dirección TCP real del peer, que el
        // cliente no puede falsificar.
        String clientIp = request.getRemoteAddr();

        if (loginRateLimiter.isLocked(clientIp)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(Map.of("error", "Demasiados intentos fallidos. Reintentá más tarde."));
        }

        Session session;
        try {
            session = loginUseCase.execute(req.getEmail(), req.getPassword());
        } catch (RuntimeException invalidCredentials) {
            loginRateLimiter.recordFailure(clientIp);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Credenciales inválidas"));
        }
        loginRateLimiter.recordSuccess(clientIp);

        User user = userRepository.findByEmail(req.getEmail()).orElseThrow(() -> new RuntimeException("User not found after login"));
        String access = jwtService.generateAccessToken(user.id(), user.tenantId(), user.role(), user.serviceId());

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

    /** FR-ID-02 — el usuario invitado fija su contraseña con el token que recibió y activa su cuenta. */
    @PostMapping("/accept-invite")
    public ResponseEntity<?> acceptInvite(@RequestBody AcceptInvitationRequest req) {
        try {
            acceptInvitationUseCase.execute(req.getToken(), req.getPassword());
        } catch (InvalidInvitationTokenException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        }
        return ResponseEntity.ok(Map.of("activated", true));
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
        String access = jwtService.generateAccessToken(user.id(), user.tenantId(), user.role(), user.serviceId());

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
