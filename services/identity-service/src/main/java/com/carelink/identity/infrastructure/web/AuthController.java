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
import com.carelink.identity.domain.port.EmailNotifier;
import com.carelink.identity.domain.port.PasswordEncoder;
import com.carelink.identity.domain.port.SchemaProvisioner;
import com.carelink.identity.domain.port.SessionRepository;
import com.carelink.identity.domain.port.TenantRepository;
import com.carelink.identity.domain.port.UserRepository;
import com.carelink.identity.domain.port.VerificationTokenRepository;
import com.carelink.identity.infrastructure.security.JwtService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/auth")
public final class AuthController {

    /** Default refresh token TTL in seconds. */
    private static final long DEFAULT_REFRESH_TTL_SECONDS = 604800L;

    /** Tenant registration use case. */
    private final RegisterTenantUseCase registerTenantUseCase;

    /** Login use case. */
    private final LoginUseCase loginUseCase;

    /** Email verification use case. */
    private final VerifyEmailUseCase verifyEmailUseCase;

    /** Refresh-token use case. */
    private final RefreshTokenUseCase refreshTokenUseCase;

    /** Logout use case. */
    private final LogoutUseCase logoutUseCase;

    /** JWT service. */
    private JwtService jwtService;

    /** User repository. */
    private final UserRepository userRepository;

    /**
     * Builds auth controller.
     *
     * @param tenantRepository tenant repository
     * @param userRepositoryValue user repository
     * @param schemaProvisioner schema provisioner
     * @param emailNotifier email notifier
     * @param passwordEncoder password encoder
     * @param tokenRepository verification token repository
     * @param sessionRepository session repository
     */
    public AuthController(
            final TenantRepository tenantRepository,
            final UserRepository userRepositoryValue,
            final SchemaProvisioner schemaProvisioner,
            final EmailNotifier emailNotifier,
            final PasswordEncoder passwordEncoder,
            final VerificationTokenRepository tokenRepository,
            final SessionRepository sessionRepository) {
        this.registerTenantUseCase = new RegisterTenantUseCase(
            tenantRepository,
            userRepositoryValue,
            schemaProvisioner,
            emailNotifier,
            passwordEncoder,
            tokenRepository
        );
        this.loginUseCase = new LoginUseCase(
            userRepositoryValue,
            passwordEncoder,
            sessionRepository
        );
        this.verifyEmailUseCase = new VerifyEmailUseCase(tokenRepository);
        this.refreshTokenUseCase = new RefreshTokenUseCase(sessionRepository);
        this.logoutUseCase = new LogoutUseCase(sessionRepository);
        this.userRepository = userRepositoryValue;
    }

    /**
     * Injects JWT service.
     *
     * @param jwtServiceValue jwt service
     */
    @Autowired
    public void setJwtService(final JwtService jwtServiceValue) {
        this.jwtService = jwtServiceValue;
    }

    /**
     * Registers a new tenant and its admin user.
     *
     * @param req registration payload
     * @return created tenant identifiers
     */
    @PostMapping("/register")
        public ResponseEntity<?> register(
            @RequestBody final RegisterTenantRequest req) {
        final Tenant tenant = registerTenantUseCase.execute(
            req.getName(),
            req.getSlug(),
            req.getTaxId(),
            req.getAdminEmail(),
            req.getPassword()
        );
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(Map.of(
                "tenantId", tenant.id().toString(),
                "slug", tenant.slug().value()
            ));
    }

    /**
     * Authenticates a user and sets refresh cookie.
     *
     * @param req login payload
     * @return access token response
     */
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(
            @RequestBody final LoginRequest req) {
        final Session session = loginUseCase.execute(
            req.getEmail(),
            req.getPassword()
        );
        final User user = userRepository.findByEmail(req.getEmail())
            .orElseThrow(
                () -> new RuntimeException("User not found after login")
            );
        final String access = jwtService.generateAccessToken(
            user.id(),
            user.tenantId(),
            user.role()
        );

        final long maxAge = readRefreshTokenTtlSeconds();
        final ResponseCookie cookie = ResponseCookie
            .from("refresh_token", session.refreshToken())
                .httpOnly(true)
                .secure(true)
                .path("/")
                .sameSite("Strict")
                .maxAge(maxAge)
                .build();

        return ResponseEntity.ok()
            .header(HttpHeaders.SET_COOKIE, cookie.toString())
            .body(new AuthResponse(access));
    }

    /**
     * Marks email as verified using the token.
     *
     * @param token verification token
     * @return verification result
     */
    @PostMapping("/verify")
    public ResponseEntity<?> verify(@RequestParam("token") final String token) {
        userIdCheck(verifyEmailUseCase.execute(token));
        return ResponseEntity.ok(Map.of("verified", true));
    }

    /**
     * Rotates refresh token and emits a new access token.
     *
     * @param req optional request-body token
     * @param request servlet request for cookie fallback
     * @return access token response
     */
    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(
            @RequestBody(required = false) final RefreshRequest req,
            final HttpServletRequest request) {
        String token = null;
        if (req != null
                && req.getRefreshToken() != null
                && !req.getRefreshToken().isBlank()) {
            token = req.getRefreshToken();
        }
        if (token == null) {
            final Cookie[] cookies = request.getCookies();
            if (cookies != null) {
                for (Cookie c : cookies) {
                    if ("refresh_token".equals(c.getName())) {
                        token = c.getValue();
                        break;
                    }
                }
            }
        }
        if (token == null) {
            throw new RuntimeException("No refresh token provided");
        }

        final Session session = refreshTokenUseCase.execute(token);
        final User user = userRepository.findById(session.userId())
            .orElseThrow(
                () -> new RuntimeException("User not found for session")
            );
        final String access = jwtService.generateAccessToken(
            user.id(),
            user.tenantId(),
            user.role()
        );

        final long maxAge = readRefreshTokenTtlSeconds();
        final ResponseCookie cookie = ResponseCookie
            .from("refresh_token", session.refreshToken())
                .httpOnly(true)
                .secure(true)
                .path("/")
                .sameSite("Strict")
                .maxAge(maxAge)
                .build();

        return ResponseEntity.ok()
            .header(HttpHeaders.SET_COOKIE, cookie.toString())
            .body(new AuthResponse(access));
    }

    /**
     * Revokes refresh token and clears cookie.
     *
     * @param req optional request-body token
     * @param request servlet request for cookie fallback
     * @return no-content response
     */
    @PostMapping("/logout")
    public ResponseEntity<?> logout(
            @RequestBody(required = false) final RefreshRequest req,
            final HttpServletRequest request) {
        String token = null;
        if (req != null
                && req.getRefreshToken() != null
                && !req.getRefreshToken().isBlank()) {
            token = req.getRefreshToken();
        }
        if (token == null) {
            final Cookie[] cookies = request.getCookies();
            if (cookies != null) {
                for (Cookie c : cookies) {
                    if ("refresh_token".equals(c.getName())) {
                        token = c.getValue();
                        break;
                    }
                }
            }
        }
        if (token != null) {
            logoutUseCase.execute(token);
        }

        final ResponseCookie expired = ResponseCookie.from("refresh_token", "")
            .httpOnly(true)
            .secure(true)
            .path("/")
            .maxAge(0)
            .build();
        return ResponseEntity.noContent()
            .header(HttpHeaders.SET_COOKIE, expired.toString())
            .build();
    }

    private long readRefreshTokenTtlSeconds() {
        return Long.parseLong(System.getenv().getOrDefault(
            "REFRESH_TOKEN_TTL_SECONDS",
            String.valueOf(DEFAULT_REFRESH_TTL_SECONDS)
        ));
    }

    private void userIdCheck(final UUID id) {
        // Placeholder: verification use case already enforces token validity.
    }
}
