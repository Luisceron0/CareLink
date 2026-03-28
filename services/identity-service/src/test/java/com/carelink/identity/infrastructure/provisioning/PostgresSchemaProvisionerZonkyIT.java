package com.carelink.identity.infrastructure.provisioning;

import com.opentable.db.postgres.embedded.EmbeddedPostgres;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.*;

public class PostgresSchemaProvisionerZonkyIT {

    @Test
    public void provisionCreatesSchema() throws Exception {
        try (EmbeddedPostgres pg = EmbeddedPostgres.builder().setPort(0).start()) {
            javax.sql.DataSource ds = pg.getPostgresDatabase();
            JdbcTemplate jdbc = new JdbcTemplate(ds);

            PostgresSchemaProvisioner provisioner = new PostgresSchemaProvisioner(jdbc, new DefaultResourceLoader());
            provisioner.provisionSchema("ittenant");

            Integer count = jdbc.queryForObject(
                    "SELECT count(*) FROM information_schema.schemata WHERE schema_name = ?",
                    Integer.class,
                    "tenant_ittenant");
            assertNotNull(count);
            assertEquals(1, count.intValue());
        }
    }
}
