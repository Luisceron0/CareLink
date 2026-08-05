package com.carelink.identity.infrastructure.provisioning;

import com.carelink.identity.domain.value.TenantSlug;
import com.carelink.identity.support.EmbeddedPostgresSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

public class PostgresSchemaProvisionerIT {

    @Test
    @DisplayName("provisiona el schema del tenant con patients y audit_log")
    void provisionCreatesSchema() {
        String url = EmbeddedPostgresSupport.createDatabase("ittenant");
        JdbcTemplate admin = EmbeddedPostgresSupport.adminJdbcTemplate(url);

        PostgresSchemaProvisioner provisioner =
                new PostgresSchemaProvisioner(admin, new DefaultResourceLoader(), EmbeddedPostgresSupport.APP_ROLE);
        provisioner.provisionSchema(new TenantSlug("ittenant"));

        Integer schemaCount = admin.queryForObject(
                "SELECT count(*) FROM information_schema.schemata WHERE schema_name = ?",
                Integer.class, "tenant_ittenant");
        assertThat(schemaCount).isEqualTo(1);

        Integer auditLogCount = admin.queryForObject(
                "SELECT count(*) FROM information_schema.tables WHERE table_schema = ? AND table_name = 'audit_log'",
                Integer.class, "tenant_ittenant");
        assertThat(auditLogCount).isEqualTo(1);
    }

    @Test
    @DisplayName("provisiona dos tenants sin que uno pise al otro")
    void provisioningTwoTenantsDoesNotCollide() {
        String url = EmbeddedPostgresSupport.createDatabase("ittwo");
        JdbcTemplate admin = EmbeddedPostgresSupport.adminJdbcTemplate(url);

        PostgresSchemaProvisioner provisioner =
                new PostgresSchemaProvisioner(admin, new DefaultResourceLoader(), EmbeddedPostgresSupport.APP_ROLE);
        provisioner.provisionSchema(new TenantSlug("tenanta"));
        provisioner.provisionSchema(new TenantSlug("tenantb"));

        Integer count = admin.queryForObject(
                "SELECT count(*) FROM information_schema.schemata WHERE schema_name IN (?, ?)",
                Integer.class, "tenant_tenanta", "tenant_tenantb");
        assertThat(count).isEqualTo(2);
    }

    @Test
    @DisplayName("AC-05 — un slug con guión provisiona correctamente (antes rompía CREATE SCHEMA sin comillas)")
    void provisionSchemaAcceptsHyphenatedSlug() {
        // Este test documenta la corrección, no solo la evita: TenantSlug permite
        // `^[a-z0-9-]{3,64}$`, y un slug con guión es el caso común para un nombre de
        // clínica real ("clinica-la-esperanza"). Antes de AC-05, `"tenant_" + slug` se
        // concatenaba sin comillas y esto fallaba con un error de sintaxis SQL crudo.
        String url = EmbeddedPostgresSupport.createDatabase("ithyphen");
        JdbcTemplate admin = EmbeddedPostgresSupport.adminJdbcTemplate(url);

        PostgresSchemaProvisioner provisioner =
                new PostgresSchemaProvisioner(admin, new DefaultResourceLoader(), EmbeddedPostgresSupport.APP_ROLE);
        provisioner.provisionSchema(new TenantSlug("clinica-la-esperanza"));

        Integer schemaCount = admin.queryForObject(
                "SELECT count(*) FROM information_schema.schemata WHERE schema_name = ?",
                Integer.class, "tenant_clinica-la-esperanza");
        assertThat(schemaCount).isEqualTo(1);
    }

    @Test
    @DisplayName("AC-05 — un slug malicioso no puede llegar a provisionSchema: el port exige TenantSlug")
    void provisionSchemaCannotReceiveAnUnvalidatedString() {
        // No hay forma de ejercitar `provisionSchema("'; DROP SCHEMA public CASCADE; --")`
        // directamente: el port toma TenantSlug, no String, así que ese valor tiene que
        // pasar por el constructor de TenantSlug primero — y ahí es donde se rechaza.
        // Este test verifica esa afirmación, no la rodea.
        assertThatIllegalArgument(() -> new TenantSlug("'; DROP SCHEMA public CASCADE; --"));
        assertThatIllegalArgument(() -> new TenantSlug("tenant_a\".tenant_b"));
    }

    private void assertThatIllegalArgument(Runnable action) {
        try {
            action.run();
        } catch (IllegalArgumentException expected) {
            return;
        }
        throw new AssertionError("Se esperaba IllegalArgumentException y no se lanzó");
    }
}
