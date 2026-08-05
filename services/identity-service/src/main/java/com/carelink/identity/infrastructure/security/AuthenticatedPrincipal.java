package com.carelink.identity.infrastructure.security;

import java.util.UUID;

/**
 * Lo que {@link JwtAuthenticationFilter} extrae de un JWT válido y deja en el
 * {@code SecurityContext} como principal — no un {@code String} suelto. Antes del
 * cambio que introdujo esta clase, el principal era literalmente {@code claims.getSubject()}
 * (el user id como texto): alcanzaba para autenticar, pero cualquier código que
 * necesitara el tenant del request autenticado no tenía de dónde sacarlo sin volver a
 * parsear el JWT a mano.
 *
 * <p>{@code tenantId} es lo que hace posible que un endpoint clínico sepa en qué schema
 * de tenant operar sin que el cliente se lo diga en el request — si el tenant viniera
 * de un parámetro o de un header en vez de un claim ya firmado, cualquiera podría pedir
 * datos de un tenant ajeno con solo cambiar ese valor (exactamente la fila "Cross-tenant
 * PHI access" del STRIDE, SRS §8.1).
 *
 * <p>Implementa {@code org.springframework.security.core.AuthenticatedPrincipal} —no
 * es decorativo. {@code AbstractAuthenticationToken.getName()} (lo que
 * {@code AuditAspect} usa para identificar al usuario actual) hace
 * {@code instanceof} contra ESA interfaz específica de Spring Security antes de caer a
 * {@code principal.toString()}. Sin implementarla, {@code getName()} habría devuelto la
 * representación completa del record (todos los campos) en vez del UUID — un cambio de
 * comportamiento silencioso que rompía la identificación de usuario en el audit log sin
 * ningún error visible.
 */
public record AuthenticatedPrincipal(UUID userId, UUID tenantId, String role)
        implements org.springframework.security.core.AuthenticatedPrincipal {

    @Override
    public String getName() {
        return userId.toString();
    }
}
