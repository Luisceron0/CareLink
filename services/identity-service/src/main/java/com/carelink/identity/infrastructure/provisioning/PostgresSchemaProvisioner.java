package com.carelink.identity.infrastructure.provisioning;

import com.carelink.identity.domain.port.SchemaProvisioner;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Component
public class PostgresSchemaProvisioner implements SchemaProvisioner {

    private static final String TENANT_TEMPLATE = "classpath:/db/tenant/tenant_template.sql";

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

            // Solo classpath. Antes había tres rutas de fallback —`classpath:/migrations/`,
            // `file:../migrations/`, `file:./migrations/`— que dependían del directorio de
            // trabajo del proceso y no acertaban en ningún layout real: ninguna resuelve
            // desde services/identity-service, y dentro del contenedor la aplicación es un
            // jar, donde `file:../` no existe. Provisionar un tenant habría fallado en
            // runtime. No se detectó antes porque el único test que lo cubría terminaba en
            // `IT` y failsafe no estaba configurado, así que nunca se ejecutó.
            Resource resource = resourceLoader.getResource(TENANT_TEMPLATE);
            if (!resource.exists()) {
                throw new IllegalStateException("Plantilla de schema de tenant no encontrada: " + TENANT_TEMPLATE);
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
