package com.carelink.identity.infrastructure.audit;

import com.carelink.identity.domain.audit.AuditEntry;
import com.carelink.identity.domain.port.AuditEntryPort;
import com.carelink.identity.domain.value.TenantSlug;
import com.carelink.identity.infrastructure.persistence.PostgresIdentifiers;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Escribe cada {@link AuditEntry} en {@code tenant_<slug>.audit_log} usando el
 * {@code JdbcTemplate} primario — el rol restringido de la aplicación, que tiene
 * INSERT y SELECT sobre esa tabla y nada más (AC-10). Esta clase nunca corre con el
 * rol administrador: no lo necesita, y no debería poder usarlo.
 */
@Component
public class JdbcAuditEntryAdapter implements AuditEntryPort {

    private final JdbcTemplate jdbcTemplate;

    public JdbcAuditEntryAdapter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void record(AuditEntry entry) {
        String schema = validatedSchema(entry.tenantSlug());

        jdbcTemplate.update(
                "INSERT INTO " + schema + ".audit_log " +
                        "(user_id, role, patient_id, action, service_id, source_ip, session_id, result, occurred_at) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                entry.userId(),
                entry.role(),
                entry.patientId(),
                entry.action(),
                entry.serviceId(),
                entry.sourceIp(),
                entry.sessionId(),
                entry.result().name(),
                entry.occurredAt());
    }

    private String validatedSchema(String tenantSlug) {
        // Mismo patrón que TenantSlug (AC-05) — una sola fuente de verdad del formato
        // válido, no una copia que puede desalinearse. El comillado de
        // PostgresIdentifiers es la segunda capa: acepta el guión que TenantSlug
        // permite y que un identificador sin comillas no admite.
        if (!TenantSlug.PATTERN.matcher(tenantSlug).matches()) {
            throw new IllegalArgumentException(
                    "tenantSlug rechazado en el sink de auditoría: " + tenantSlug);
        }
        return PostgresIdentifiers.quote("tenant_" + tenantSlug);
    }
}
