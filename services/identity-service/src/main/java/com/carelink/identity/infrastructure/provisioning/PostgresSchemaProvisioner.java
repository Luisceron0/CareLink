package com.carelink.identity.infrastructure.provisioning;

import com.carelink.identity.domain.port.SchemaProvisioner;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Component
public class PostgresSchemaProvisioner implements SchemaProvisioner {
    private final JdbcTemplate jdbcTemplate;
    private final ResourceLoader resourceLoader;

    public PostgresSchemaProvisioner(JdbcTemplate jdbcTemplate, ResourceLoader resourceLoader) {
        this.jdbcTemplate = jdbcTemplate;
        this.resourceLoader = resourceLoader;
    }

    @Override
    public void provisionSchema(String tenantSlug) {
        try {
            String schema = "tenant_" + tenantSlug;
            jdbcTemplate.execute("CREATE SCHEMA IF NOT EXISTS " + schema);

            Resource resource = resourceLoader.getResource("classpath:/migrations/002_tenant_schema_template.sql");
            if (!resource.exists()) {
                resource = resourceLoader.getResource("file:../migrations/002_tenant_schema_template.sql");
            }
            if (!resource.exists()) {
                resource = resourceLoader.getResource("file:./migrations/002_tenant_schema_template.sql");
            }
            if (!resource.exists()) {
                throw new RuntimeException("Migration template not found: migrations/002_tenant_schema_template.sql");
            }

            String sql = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

            // Prefix table creation with schema name
            sql = sql.replaceAll("(?i)CREATE TABLE IF NOT EXISTS\\s+(\\w+)", "CREATE TABLE IF NOT EXISTS " + schema + ".$1");

            // Split statements and execute
            for (String stmt : sql.split(";")) {
                String s = stmt.trim();
                if (!s.isEmpty()) jdbcTemplate.execute(s);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to provision schema", e);
        }
    }
}
