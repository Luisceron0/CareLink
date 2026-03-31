package com.carelink.clinical.infrastructure.audit;

import com.carelink.clinical.domain.port.AuditLogPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Adaptador de auditoría de PHI con escritura insert-only.
 */
@Component
@Primary
@ConditionalOnBean(DataSource.class)
public final class PhiAuditLogAdapter implements AuditLogPort {

    /** Índice SQL para tenant_id. */
    private static final int INDEX_TENANT_ID = 1;

    /** Índice SQL para actor_user_id. */
    private static final int INDEX_ACTOR_USER_ID = 2;

    /** Índice SQL para target_patient_id. */
    private static final int INDEX_TARGET_PATIENT_ID = 3;

    /** Índice SQL para action. */
    private static final int INDEX_ACTION = 4;

    /** Índice SQL para occurred_at. */
    private static final int INDEX_OCCURRED_AT = 5;

    /** SQL de inserción de auditoría. */
    private static final String INSERT_SQL =
            "INSERT INTO phi_audit_log "
                + "(tenant_id, actor_user_id, target_patient_id, "
                + "action, occurred_at) "
                    + "VALUES (?, ?, ?, ?, ?)";

    /** DataSource de infraestructura. */
    private final DataSource dataSource;

    /**
     * Constructor.
     *
     * @param dataSourceArg datasource
     */
    public PhiAuditLogAdapter(final DataSource dataSourceArg) {
        this.dataSource = Objects.requireNonNull(dataSourceArg);
    }

    @Override
    public void recordPhiAccess(final UUID tenantId,
                                final UUID actorUserId,
                                final UUID targetPatientId,
                                final String action) {
        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(actorUserId);
        Objects.requireNonNull(targetPatientId);
        Objects.requireNonNull(action);

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement =
                     connection.prepareStatement(INSERT_SQL)) {
            statement.setObject(INDEX_TENANT_ID, tenantId);
            statement.setObject(INDEX_ACTOR_USER_ID, actorUserId);
            statement.setObject(INDEX_TARGET_PATIENT_ID, targetPatientId);
            statement.setString(INDEX_ACTION, action);
            statement.setObject(INDEX_OCCURRED_AT, Instant.now());
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException(
                    "Unable to persist PHI audit log",
                    exception
            );
        }
    }
}
