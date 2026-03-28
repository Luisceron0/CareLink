package com.carelink.identity.infrastructure.provisioning;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Disabled;
import org.testcontainers.containers.PostgreSQLContainer;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.core.io.DefaultResourceLoader;

import static org.junit.jupiter.api.Assertions.*;

@Disabled("Integration test - requires Docker/Testcontainers. Enable locally or in CI.")
public class PostgresSchemaProvisionerIT {
    @Test
    public void provisionCreatesSchema() {
        try (PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:15-alpine")) {
            pg.start();

            DriverManagerDataSource ds = new DriverManagerDataSource(pg.getJdbcUrl(), pg.getUsername(), pg.getPassword());
            JdbcTemplate jdbc = new JdbcTemplate(ds);

            PostgresSchemaProvisioner provisioner = new PostgresSchemaProvisioner(jdbc, new DefaultResourceLoader());
            provisioner.provisionSchema("ittenant");

            Integer count = jdbc.queryForObject("SELECT count(*) FROM information_schema.schemata WHERE schema_name = ?", Integer.class, "tenant_ittenant");
            assertNotNull(count);
            assertEquals(1, count.intValue());
        }
    }
}
