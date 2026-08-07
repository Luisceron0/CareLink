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
public record AuthenticatedPrincipal(UUID userId, UUID tenantId, String role, String serviceId)
        implements org.springframework.security.core.AuthenticatedPrincipal {

    /**
     * Roles que ven todo el tenant sin filtrar por servicio (§4: {@code TENANT_ADMIN}
     * "Full within tenant"; {@code AUDITOR} "Audit log only, no PHI read path" — no se
     * le restringe por servicio porque su alcance ya está restringido por otra vía).
     * El resto de los roles ve solo su propio {@code service_id} — AC-06b.
     */
    private static final java.util.Set<String> SERVICE_SCOPE_EXEMPT_ROLES =
            java.util.Set.of("TENANT_ADMIN", "AUDITOR");

    @Override
    public String getName() {
        return userId.toString();
    }

    /**
     * El {@code service_id} por el que hay que filtrar las lecturas clínicas de este
     * principal, o {@code null} si no corresponde filtrar (AC-06b).
     *
     * <p>Devolver {@code null} acá significa "sin filtro", así que un rol NO exento con
     * {@code serviceId} nulo tendría acceso a todo el tenant — exactamente lo contrario
     * de lo que AC-06b pide. Por eso ese caso NO llega hasta acá: los controllers
     * rechazan con 403 a un principal no exento sin {@code serviceId} antes de
     * consultar este método (ver {@code requireServiceScope} en cada controller
     * clínico). Un usuario sin servicio asignado no ve nada, no lo ve todo.
     */
    public String serviceScopeFilter() {
        return SERVICE_SCOPE_EXEMPT_ROLES.contains(role) ? null : serviceId;
    }

    /** true si este rol ve todo el tenant sin filtrar por servicio. */
    public boolean isServiceScopeExempt() {
        return SERVICE_SCOPE_EXEMPT_ROLES.contains(role);
    }
}
