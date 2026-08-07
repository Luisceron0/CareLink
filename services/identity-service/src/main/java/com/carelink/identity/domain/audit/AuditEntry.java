package com.carelink.identity.domain.audit;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Una fila de {@code audit_log} (FR-CLN-13, SRS §5.10): timestamp, usuario, rol,
 * paciente, acción, service_id, IP de origen, ID de sesión.
 *
 * <p>Inmutable — se construye completa o no se construye. No hay setters que
 * permitan armarla a medias y dejar que un {@code @Auditable} futuro se olvide de
 * completar un campo antes de persistirla.
 *
 * <p>{@code tenantSlug} viaja sin el prefijo {@code tenant_} ni validar acá — la
 * validación al concatenarlo en el nombre de la tabla es responsabilidad del sink
 * que arma el SQL ({@code JdbcAuditEntryAdapter}), no de este value object, siguiendo
 * la misma lección de ADR-010: la invariante de seguridad se codifica en el tipo o
 * en el sink que cruza a SQL, no en la disciplina de quien construye el objeto.
 */
public record AuditEntry(
        String tenantSlug,
        UUID userId,
        String role,
        UUID patientId,
        String action,
        String serviceId,
        String sourceIp,
        UUID sessionId,
        AuditResult result,
        OffsetDateTime occurredAt
) {
    public AuditEntry {
        if (tenantSlug == null || tenantSlug.isBlank()) {
            throw new IllegalArgumentException("AuditEntry requiere tenantSlug");
        }
        if (action == null || action.isBlank()) {
            throw new IllegalArgumentException("AuditEntry requiere action");
        }
        if (result == null) {
            throw new IllegalArgumentException("AuditEntry requiere result");
        }
        if (occurredAt == null) {
            throw new IllegalArgumentException("AuditEntry requiere occurredAt");
        }
    }
}
