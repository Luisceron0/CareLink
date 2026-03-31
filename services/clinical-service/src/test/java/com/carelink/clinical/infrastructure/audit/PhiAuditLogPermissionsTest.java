package com.carelink.clinical.infrastructure.audit;

import com.opentable.db.postgres.embedded.EmbeddedPostgres;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifica permisos insert-only sobre phi_audit_log.
 */
public class PhiAuditLogPermissionsTest {

    @Test
    void deleteIsRejectedByDatabasePermissions() throws Exception {
        try (EmbeddedPostgres postgres = EmbeddedPostgres.start()) {
            final String jdbcUrl = postgres.getJdbcUrl("postgres", "postgres");

            try (Connection admin = postgres.getPostgresDatabase().getConnection()) {
                createSchemaAndRole(admin);
                insertAuditRow(admin);
            }

            try (Connection appConnection =
                         java.sql.DriverManager.getConnection(
                                 jdbcUrl,
                                 "postgres",
                                 "postgres"
                         );
                 Statement statement = appConnection.createStatement()) {
                statement.execute("SET ROLE app_clinical_user");

                assertThrows(
                        SQLException.class,
                        () -> statement.executeUpdate(
                                "DELETE FROM phi_audit_log WHERE action = 'PATIENT_READ'"
                        )
                );
            }

            try (Connection admin = postgres.getPostgresDatabase().getConnection();
                 Statement verify = admin.createStatement();
                 ResultSet rs = verify.executeQuery("SELECT COUNT(*) FROM phi_audit_log")) {
                assertTrue(rs.next());
                assertTrue(rs.getInt(1) > 0);
            }
        }
    }

    private static void createSchemaAndRole(final Connection admin)
            throws SQLException {
        try (Statement statement = admin.createStatement()) {
            statement.execute(
                    "CREATE TABLE phi_audit_log ("
                            + "id BIGSERIAL PRIMARY KEY, "
                            + "tenant_id UUID NOT NULL, "
                            + "actor_user_id UUID NOT NULL, "
                            + "target_patient_id UUID NOT NULL, "
                            + "action VARCHAR(80) NOT NULL, "
                            + "occurred_at TIMESTAMPTZ NOT NULL DEFAULT now()"
                            + ")"
            );
            statement.execute("CREATE ROLE app_clinical_user");
            statement.execute("GRANT USAGE ON SCHEMA public TO app_clinical_user");
            statement.execute(
                    "GRANT SELECT, INSERT ON TABLE phi_audit_log TO app_clinical_user"
            );
            statement.execute(
                    "REVOKE UPDATE, DELETE ON TABLE phi_audit_log "
                            + "FROM app_clinical_user"
            );
        }
    }

    private static void insertAuditRow(final Connection admin) throws SQLException {
        final String insertSql =
                "INSERT INTO phi_audit_log "
                        + "(tenant_id, actor_user_id, target_patient_id, action) "
                        + "VALUES (?, ?, ?, ?)";
        try (PreparedStatement statement = admin.prepareStatement(insertSql)) {
            statement.setObject(1, java.util.UUID.randomUUID());
            statement.setObject(2, java.util.UUID.randomUUID());
            statement.setObject(3, java.util.UUID.randomUUID());
            statement.setString(4, "PATIENT_READ");
            statement.executeUpdate();
        }
    }
}
