package com.carelink.identity.infrastructure.provisioning;

import com.carelink.identity.domain.port.SchemaProvisioner;
import com.carelink.identity.domain.value.TenantSlug;
import com.carelink.identity.infrastructure.persistence.PostgresIdentifiers;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.sql.Statement;

@Component
public class PostgresSchemaProvisioner implements SchemaProvisioner {

    private static final String TENANT_TEMPLATE = "classpath:/db/tenant/tenant_template.sql";

    private final JdbcTemplate jdbcTemplate;
    private final ResourceLoader resourceLoader;
    private final String appRole;

    /**
     * El {@code JdbcTemplate} inyectado acá tiene que ser el administrador, no el
     * primario. Este método ejecuta {@code CREATE SCHEMA}, {@code CREATE TABLE},
     * {@code CREATE TRIGGER} y {@code GRANT} — el rol restringido de la aplicación
     * no tiene permiso para nada de eso, y no debería tenerlo: si lo tuviera,
     * sería dueño de las tablas que crea, y un dueño de tabla en PostgreSQL
     * siempre puede volver a otorgarse cualquier permiso que se le revoque
     * (AC-10 dejaría de significar algo).
     *
     * <p>{@code appRole} se lee de {@code spring.datasource.username} — la misma
     * propiedad que define con qué rol conecta el resto de la aplicación — para
     * que el nombre del role al que se le hace GRANT en la plantilla de tenant
     * nunca pueda desalinearse del rol que realmente usa el pool de conexiones.
     */
    public PostgresSchemaProvisioner(
            @Qualifier("adminJdbcTemplate") JdbcTemplate jdbcTemplate,
            ResourceLoader resourceLoader,
            @Value("${spring.datasource.username}") String appRole) {
        this.jdbcTemplate = jdbcTemplate;
        this.resourceLoader = resourceLoader;
        this.appRole = appRole;
    }

    @Override
    public void provisionSchema(TenantSlug tenantSlug) {
        // AC-05, defensa en profundidad: el port ya exige TenantSlug, pero este sink
        // revalida el valor crudo contra el MISMO patrón (TenantSlug.PATTERN, no una
        // copia) en vez de confiar ciegamente en que el objeto que llegó pasó
        // correctamente por el constructor en algún punto anterior del código. Ver la
        // lección de ADR-010: el riesgo no es que TenantSlug no valide, es que un
        // caller futuro construya el String de otra forma antes de llegar acá.
        String rawSlug = tenantSlug.value();
        if (!TenantSlug.PATTERN.matcher(rawSlug).matches()) {
            throw new IllegalArgumentException("TenantSlug rechazado en el sink de provisioning: " + rawSlug);
        }

        // Comillado, no solo validado: un identificador entre comillas dobles acepta
        // cualquier carácter (incluido el guión, que sin comillas rompía la sintaxis
        // de CREATE SCHEMA — bug encontrado escribiendo los tests de Sub-fase 1,
        // PostgresSchemaProvisionerIT). Es la segunda capa después de la validación
        // del regex, no un sustituto de ella.
        String schema = PostgresIdentifiers.quote("tenant_" + rawSlug);
        // Sustitución de configuración de despliegue, no de entrada de usuario en
        // runtime: `appRole` viene de spring.datasource.username (env var del
        // operador), mismo nivel de confianza que POSTGRES_USER. No es el tipo de
        // dato contra el que §8.4 pide defenderse.
        String templateSql = readTemplate().replace("{{app_role}}", appRole);

        jdbcTemplate.execute((ConnectionCallback<Void>) conn -> {
            boolean previousAutoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("CREATE SCHEMA IF NOT EXISTS " + schema);

                // GRANT sobre una tabla no alcanza: en PostgreSQL, para tocar
                // CUALQUIER objeto dentro de un schema hace falta además el
                // privilegio USAGE sobre el schema en sí — son dos capas de
                // permiso independientes. tenant_template.sql ya le da a
                // {{app_role}} SELECT/INSERT sobre audit_log; esto es lo que le
                // permite siquiera llegar hasta esa tabla.
                stmt.execute("GRANT USAGE ON SCHEMA " + schema + " TO " + appRole);

                // SET LOCAL, no SET: solo dura la transacción actual. Un SET liso
                // quedaría pegado a la conexión más allá de este método, y esa
                // conexión vuelve al pool de HikariCP para que la use cualquier
                // otra operación — filtrar el search_path de un tenant hacia una
                // consulta ajena sería un bug de aislamiento silencioso, no una
                // excepción ruidosa.
                stmt.execute("SET LOCAL search_path TO " + schema + ", public");

                // Una sola llamada con el archivo entero, no partido por `;` del
                // lado del cliente. El protocolo simple de Postgres separa las
                // sentencias del lado del servidor, incluidos bloques PL/pgSQL con
                // `;` internos — como la función del trigger append-only de
                // audit_log. Partir el texto por `;` en Java, que es lo que hacía
                // la versión anterior, corta esos bloques a la mitad; nunca se
                // detectó porque hasta ahora la plantilla solo tenía
                // CREATE TABLE sin funciones.
                stmt.execute(templateSql);

                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(previousAutoCommit);
            }
            return null;
        });
    }

    private String readTemplate() {
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
        try {
            return new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo leer la plantilla de schema de tenant", e);
        }
    }
}
