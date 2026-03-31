package com.carelink.identity.infrastructure.provisioning;

import com.carelink.identity.domain.port.SchemaProvisioner;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Component
public final class PostgresSchemaProvisioner implements SchemaProvisioner {

    /** Classpath migration template location. */
    private static final String TEMPLATE_CLASSPATH =
        "classpath:/migrations/002_tenant_schema_template.sql";

    /** Parent-folder migration template location. */
    private static final String TEMPLATE_PARENT_PATH =
        "file:../migrations/002_tenant_schema_template.sql";

    /** Local migration template location. */
    private static final String TEMPLATE_LOCAL_PATH =
        "file:./migrations/002_tenant_schema_template.sql";

    /** SQL execution entrypoint. */
    private final JdbcTemplate jdbcTemplate;

    /** Resource loader for migration templates. */
    private final ResourceLoader resourceLoader;

    /**
     * Builds the schema provisioner.
     *
     * @param jdbcTemplateValue jdbc template
     * @param resourceLoaderValue loader for migration resources
     */
    public PostgresSchemaProvisioner(
            final JdbcTemplate jdbcTemplateValue,
            final ResourceLoader resourceLoaderValue) {
        this.jdbcTemplate = jdbcTemplateValue;
        this.resourceLoader = resourceLoaderValue;
    }

    @Override
    public void provisionSchema(final String tenantSlug) {
        try {
            final String schema = "tenant_" + tenantSlug;
            jdbcTemplate.execute("CREATE SCHEMA IF NOT EXISTS " + schema);

            Resource resource = resourceLoader.getResource(TEMPLATE_CLASSPATH);
            if (!resource.exists()) {
                resource = resourceLoader.getResource(TEMPLATE_PARENT_PATH);
            }
            if (!resource.exists()) {
                resource = resourceLoader.getResource(TEMPLATE_LOCAL_PATH);
            }
            if (!resource.exists()) {
                throw new RuntimeException(
                    "Migration template not found: "
                        + "migrations/002_tenant_schema_template.sql"
                );
            }

            String sql = new String(
                resource.getInputStream().readAllBytes(),
                StandardCharsets.UTF_8
            );

            sql = sql.replaceAll(
                "(?i)CREATE TABLE IF NOT EXISTS\\s+(\\w+)",
                "CREATE TABLE IF NOT EXISTS " + schema + ".$1"
            );

            for (String stmt : sql.split(";")) {
                final String statement = stmt.trim();
                if (!statement.isEmpty()) {
                    jdbcTemplate.execute(statement);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to provision schema", e);
        }
    }
}
