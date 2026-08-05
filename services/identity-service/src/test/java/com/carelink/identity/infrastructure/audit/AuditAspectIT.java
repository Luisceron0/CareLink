package com.carelink.identity.infrastructure.audit;

import com.carelink.identity.domain.value.TenantSlug;
import com.carelink.identity.infrastructure.provisioning.PostgresSchemaProvisioner;
import com.carelink.identity.support.EmbeddedPostgresSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Demuestra el mecanismo {@link Auditable} + {@link AuditAspect} de punta a punta:
 * interceptar, evaluar el tenant/paciente vía SpEL, y escribir la fila en el schema
 * correcto — con el rol de aplicación, el mismo que usará el código real.
 *
 * <p>{@code DummyAuditedService} es un caso de uso sintético a propósito: todavía no
 * existe un caso de uso real con PHI que auditar (Patient es Sub-fase 2, ver
 * tasks/todo.md). Este test evidencia que el mecanismo funciona; no evidencia que
 * "una lectura de PHI produce una fila" — ese AC específico se re-verifica en
 * Sub-fase 2, contra un caso de uso real, no se da por probado acá.
 */
class AuditAspectIT {

    static class DummyAuditedService {
        @Auditable(
                action = "DUMMY_READ",
                tenantSlugExpression = "#tenantSlug",
                patientIdExpression = "#patientId")
        public String read(String tenantSlug, UUID patientId, boolean fail) {
            if (fail) {
                throw new IllegalStateException("boom");
            }
            return "ok";
        }
    }

    @Test
    @DisplayName("una invocación exitosa produce una fila SUCCESS con el action y el patient_id correctos")
    void successfulInvocationWritesSuccessRow() {
        String url = EmbeddedPostgresSupport.createDatabase("auditok");
        JdbcTemplate admin = EmbeddedPostgresSupport.adminJdbcTemplate(url);
        JdbcTemplate app = EmbeddedPostgresSupport.appJdbcTemplate(url);

        new PostgresSchemaProvisioner(admin, new DefaultResourceLoader(), EmbeddedPostgresSupport.APP_ROLE)
                .provisionSchema(new TenantSlug("auditoktenant"));

        UUID patientId = UUID.randomUUID();
        DummyAuditedService proxy = proxiedService(app);

        String result = proxy.read("auditoktenant", patientId, false);

        assertThat(result).isEqualTo("ok");
        Map<String, Object> row = admin.queryForMap(
                "SELECT action, result, patient_id FROM tenant_auditoktenant.audit_log");
        assertThat(row).containsEntry("action", "DUMMY_READ");
        assertThat(row).containsEntry("result", "SUCCESS");
        assertThat(row).containsEntry("patient_id", patientId);
    }

    @Test
    @DisplayName("FR-CLN-13: si la operación falla, se registra result = ERROR y la excepción se propaga igual")
    void failedInvocationWritesErrorRowAndStillPropagatesException() {
        String url = EmbeddedPostgresSupport.createDatabase("auditerr");
        JdbcTemplate admin = EmbeddedPostgresSupport.adminJdbcTemplate(url);
        JdbcTemplate app = EmbeddedPostgresSupport.appJdbcTemplate(url);

        new PostgresSchemaProvisioner(admin, new DefaultResourceLoader(), EmbeddedPostgresSupport.APP_ROLE)
                .provisionSchema(new TenantSlug("auditerrtenant"));

        DummyAuditedService proxy = proxiedService(app);

        assertThatThrownBy(() -> proxy.read("auditerrtenant", null, true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("boom");

        String result = admin.queryForObject(
                "SELECT result FROM tenant_auditerrtenant.audit_log", String.class);
        assertThat(result).isEqualTo("ERROR");
    }

    private DummyAuditedService proxiedService(JdbcTemplate appJdbcTemplate) {
        AuditAspect aspect = new AuditAspect(new JdbcAuditEntryAdapter(appJdbcTemplate));
        AspectJProxyFactory factory = new AspectJProxyFactory(new DummyAuditedService());
        factory.addAspect(aspect);
        return factory.getProxy();
    }
}
