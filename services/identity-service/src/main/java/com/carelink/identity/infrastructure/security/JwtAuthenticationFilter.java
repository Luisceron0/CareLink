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
import java.util.Collections;
import java.util.UUID;

public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtService jwtService;

    public JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        String auth = request.getHeader("Authorization");
        if (auth != null && auth.startsWith("Bearer ")) {
            String token = auth.substring(7);
            try {
                JWTClaimsSet claims = jwtService.parseAndValidate(token);
                UUID userId = UUID.fromString(claims.getSubject());
                String role = claims.getStringClaim("role");
                // Puede ser null: el usuario que registra un tenant nuevo (FR-ID-01)
                // todavía no tiene un tenant propio en ese instante del flujo. Cualquier
                // endpoint que SÍ requiera tenant tiene que rechazar explícitamente un
                // principal con tenantId nulo — no es este filtro el que decide eso.
                String tenantIdClaim = claims.getStringClaim("tenant_id");
                UUID tenantId = tenantIdClaim != null ? UUID.fromString(tenantIdClaim) : null;

                var authorities = Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + role));
                var principal = new AuthenticatedPrincipal(userId, tenantId, role);
                // El principal es el objeto tipado, no claims.getSubject() como antes.
                // authentication.getName() sigue devolviendo el userId como String —no
                // porque sí, sino porque AuthenticatedPrincipal implementa la interfaz de
                // Spring Security del mismo nombre y sobreescribe getName() explícitamente
                // (ver el javadoc de esa clase: sin eso, getName() habría devuelto el
                // record entero y AuditAspect dejaba de identificar al usuario en silencio).
                var authentication = new UsernamePasswordAuthenticationToken(principal, null, authorities);
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (Exception e) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                return;
            }
        }
        filterChain.doFilter(request, response);
    }
}
