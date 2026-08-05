package com.carelink.identity.infrastructure.audit;

import com.carelink.identity.infrastructure.provisioning.PostgresSchemaProvisioner;
import com.carelink.identity.support.EmbeddedPostgresSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * AC-10 y la fila "Audit trail tampering" del STRIDE (SRS §8.1), verificadas contra
 * PostgreSQL real — no invocando un mock que simule un permiso, sino provisionando un
 * tenant de verdad y probando el DELETE con las credenciales reales de cada rol.
 *
 * <p>Dos tests, dos capas independientes: el GRANT (lo que el rol de aplicación puede
 * pedirle al motor) y el trigger (lo que el motor permite hacer aunque el rol pueda
 * pedirlo). Postgres resuelve el permiso ANTES de invocar el trigger, así que el rol
 * de aplicación nunca llega a disparar el trigger — lo rechaza antes. Por eso hace
 * falta el segundo test con el rol administrador, que sí tiene el privilegio (es
 * dueño de la tabla) y por lo tanto sí llega al trigger.
 *
 * <p>Las aserciones miran la causa raíz, no el mensaje del nivel superior: Spring
 * arma el mensaje de {@code BadSqlGrammarException} sin incluir el texto de la
 * {@code PSQLException} subyacente, pero sí lo hace en otras excepciones traducidas
 * (como la que produce el trigger) — comportamiento no documentado y specifico de
 * cada subclase, no algo sobre lo que valga la pena depender.
 */
class AuditLogAppendOnlyIT {

    @Test
    @DisplayName("AC-10 — el rol de aplicación puede insertar y leer, no borrar ni modificar audit_log")
    void appRoleCannotDeleteOrUpdateAuditLog() {
        String url = EmbeddedPostgresSupport.createDatabase("ac10");
        JdbcTemplate admin = EmbeddedPostgresSupport.adminJdbcTemplate(url);
        JdbcTemplate app = EmbeddedPostgresSupport.appJdbcTemplate(url);

        new PostgresSchemaProvisioner(admin, new DefaultResourceLoader(), EmbeddedPostgresSupport.APP_ROLE)
                .provisionSchema("ac10tenant");

        app.update("INSERT INTO tenant_ac10tenant.audit_log (action) VALUES (?)", "TEST_INSERT");
        Integer inserted = app.queryForObject("SELECT count(*) FROM tenant_ac10tenant.audit_log", Integer.class);
        assertThat(inserted).as("el rol de aplicación necesita poder insertar y leer para auditar").isEqualTo(1);

        assertThatThrownBy(() -> app.update("DELETE FROM tenant_ac10tenant.audit_log"))
                .isInstanceOf(DataAccessException.class)
                .rootCause().hasMessageContaining("permission denied");

        assertThatThrownBy(() -> app.update("UPDATE tenant_ac10tenant.audit_log SET action = 'TAMPERED'"))
                .isInstanceOf(DataAccessException.class)
                .rootCause().hasMessageContaining("permission denied");

        String untouched = admin.queryForObject(
                "SELECT action FROM tenant_ac10tenant.audit_log", String.class);
        assertThat(untouched).isEqualTo("TEST_INSERT");
    }

    @Test
    @DisplayName("el trigger append-only bloquea UPDATE/DELETE incluso para el rol administrador")
    void triggerBlocksMutationEvenForTableOwner() {
        String url = EmbeddedPostgresSupport.createDatabase("ac10trigger");
        JdbcTemplate admin = EmbeddedPostgresSupport.adminJdbcTemplate(url);

        new PostgresSchemaProvisioner(admin, new DefaultResourceLoader(), EmbeddedPostgresSupport.APP_ROLE)
                .provisionSchema("triggertenant");

        admin.update("INSERT INTO tenant_triggertenant.audit_log (action) VALUES (?)", "TEST_INSERT");

        // El administrador SÍ tiene privilegio de DELETE (es dueño de la tabla) — a
        // diferencia del test anterior, acá el rechazo tiene que venir del trigger,
        // no del GRANT.
        assertThatThrownBy(() -> admin.update("DELETE FROM tenant_triggertenant.audit_log"))
                .isInstanceOf(DataAccessException.class)
                .rootCause().hasMessageContaining("append-only");

        assertThatThrownBy(() -> admin.update("UPDATE tenant_triggertenant.audit_log SET action = 'TAMPERED'"))
                .isInstanceOf(DataAccessException.class)
                .rootCause().hasMessageContaining("append-only");
    }
}
