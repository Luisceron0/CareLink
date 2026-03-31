package com.carelink.identity.infrastructure.security;

import com.nimbusds.jwt.JWTClaimsSet;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.text.ParseException;
import java.util.Collections;

public final class JwtAuthenticationFilter extends OncePerRequestFilter {

    /** Authorization header prefix for bearer tokens. */
    private static final String BEARER_PREFIX = "Bearer ";

    /** Length of bearer prefix. */
    private static final int BEARER_PREFIX_LENGTH = 7;

    /** JWT parser and validator. */
    private final JwtService jwtService;

    /**
     * Builds the authentication filter.
     *
     * @param jwtServiceValue jwt service
     */
    public JwtAuthenticationFilter(final JwtService jwtServiceValue) {
        this.jwtService = jwtServiceValue;
    }

    /**
     * Extracts a bearer token from the request and authenticates the user.
     *
     * @param request http request
     * @param response http response
     * @param filterChain remaining filters
     * @throws ServletException when servlet processing fails
     * @throws IOException when I/O fails
     */
    @Override
    protected void doFilterInternal(
            final HttpServletRequest request,
            final HttpServletResponse response,
            final FilterChain filterChain)
            throws ServletException, IOException {
        final String auth = request.getHeader("Authorization");
        if (auth != null && auth.startsWith(BEARER_PREFIX)) {
            final String token = auth.substring(BEARER_PREFIX_LENGTH);
            try {
                final JWTClaimsSet claims = jwtService.parseAndValidate(token);
                final String sub = claims.getSubject();
                final String role = claims.getStringClaim("role");
                final var authorities = Collections.singletonList(
                    new SimpleGrantedAuthority("ROLE_" + role)
                );
                final var authentication =
                    new UsernamePasswordAuthenticationToken(
                        sub,
                        null,
                        authorities
                    );
                SecurityContextHolder.getContext()
                    .setAuthentication(authentication);
            } catch (ParseException | RuntimeException e) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                return;
            }
        }
        filterChain.doFilter(request, response);
    }
}
