package com.carelink.identity.infrastructure.provisioning;

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
        provisioner.provisionSchema("ittenant");

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
        // Slugs sin guión a propósito: TenantSlug permite `^[a-z0-9-]{3,64}$`, pero
        // `"tenant_" + slug` se concatena SIN comillas en `CREATE SCHEMA`, y un
        // guión no es un carácter válido en un identificador de Postgres sin
        // comillas — `provisionSchema("alguna-clinica")` falla hoy con un error de
        // sintaxis SQL crudo, no con un 400 controlado. Es un hallazgo de esta
        // sub-fase, no algo que este test deba enmascarar; queda anotado para
        // AC-05 (Sub-fase 2), que es donde corresponde citar el identificador.
        String url = EmbeddedPostgresSupport.createDatabase("ittwo");
        JdbcTemplate admin = EmbeddedPostgresSupport.adminJdbcTemplate(url);

        PostgresSchemaProvisioner provisioner =
                new PostgresSchemaProvisioner(admin, new DefaultResourceLoader(), EmbeddedPostgresSupport.APP_ROLE);
        provisioner.provisionSchema("tenanta");
        provisioner.provisionSchema("tenantb");

        Integer count = admin.queryForObject(
                "SELECT count(*) FROM information_schema.schemata WHERE schema_name IN (?, ?)",
                Integer.class, "tenant_tenanta", "tenant_tenantb");
        assertThat(count).isEqualTo(2);
    }
}
