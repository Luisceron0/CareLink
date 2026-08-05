package com.carelink.identity.infrastructure.audit;

import com.carelink.identity.domain.audit.AuditEntry;
import com.carelink.identity.domain.port.AuditEntryPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * Escribe cada {@link AuditEntry} en {@code tenant_<slug>.audit_log} usando el
 * {@code JdbcTemplate} primario — el rol restringido de la aplicación, que tiene
 * INSERT y SELECT sobre esa tabla y nada más (AC-10). Esta clase nunca corre con el
 * rol administrador: no lo necesita, y no debería poder usarlo.
 */
@Component
public class JdbcAuditEntryAdapter implements AuditEntryPort {

    /**
     * Más estricto que {@code TenantSlug} (que permite guiones): un guión no es un
     * carácter válido en un identificador de Postgres sin comillas, y acá el slug
     * se concatena directo en el nombre de la tabla — validar en el sink (ADR-010)
     * significa validar contra lo que el sink realmente puede aceptar sin romperse,
     * no reusar a ciegas la validación de otra capa. Reconciliar esto con
     * {@code TenantSlug} y con {@code PostgresSchemaProvisioner} (que hoy no valida
     * nada) es AC-05, Sub-fase 2.
     */
    private static final Pattern SAFE_SCHEMA_SUFFIX = Pattern.compile("^[a-z][a-z0-9_]{2,63}$");

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
        if (!SAFE_SCHEMA_SUFFIX.matcher(tenantSlug).matches()) {
            throw new IllegalArgumentException(
                    "tenantSlug rechazado en el sink de auditoría, no es un sufijo de schema seguro: " + tenantSlug);
        }
        return "tenant_" + tenantSlug;
    }
}
