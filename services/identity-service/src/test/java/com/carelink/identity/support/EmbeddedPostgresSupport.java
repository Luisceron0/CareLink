package com.carelink.identity.support;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * PostgreSQL embebido compartido por los tests que necesitan una base real, con el
 * mismo modelo de dos roles que corre en compose y producción: un rol administrador
 * y {@code carelink_app}, el rol restringido sin grant de DELETE/UPDATE sobre
 * {@code audit_log} (AC-10).
 *
 * <p>Una sola instancia de {@link EmbeddedPostgres} por ejecución de la JVM —
 * arrancar Postgres cuesta segundos — y cada test crea su propia base dentro de esa
 * instancia, así que quedan aislados entre sí sin pagar el arranque más de una vez.
 *
 * <p>{@link #createDatabase(String)} crea la base y de inmediato provisiona el rol
 * {@code carelink_app} en ella — es el equivalente de test de
 * {@code docker/postgres-init/01-create-app-role.sh}, que en compose corre al
 * inicializar el volumen de Postgres, antes de que el backend exista. Se hace acá
 * por la misma razón: si el rol se creara desde una migración de Flyway (que corre
 * DESPUÉS de que el contexto de Spring empieza a levantar), el pool de conexiones de
 * la aplicación podría intentar conectar con un rol que todavía no existe.
 *
 * <p>Se usa Postgres de verdad, no H2 en modo compatibilidad, porque lo que estos
 * tests verifican es específico del motor: triggers, GRANTs y constraints. Un doble
 * que "se parece a Postgres" no puede evidenciar que el rol de aplicación no tiene
 * permiso de DELETE.
 */
public final class EmbeddedPostgresSupport {

    public static final String APP_ROLE = "carelink_app";
    public static final String APP_ROLE_PASSWORD = "embedded-test-app-role";
    public static final String ADMIN_USER = "postgres";
    public static final String ADMIN_PASSWORD = "postgres";

    private static volatile EmbeddedPostgres instance;

    private EmbeddedPostgresSupport() {}

    public static synchronized EmbeddedPostgres getInstance() {
        if (instance == null) {
            try {
                instance = EmbeddedPostgres.builder().setPort(0).start();
            } catch (IOException e) {
                throw new UncheckedIOException("No se pudo arrancar PostgreSQL embebido", e);
            }
        }
        return instance;
    }

    /**
     * Crea una base vacía con nombre único y provisiona en ella el rol restringido
     * {@code carelink_app}. Devuelve la URL JDBC base (sin credenciales) — quien la
     * use decide con qué rol conectarse vía {@code spring.datasource.username} /
     * {@code spring.flyway.user}, igual que en compose.
     */
    public static String createDatabase(String name) {
        String dbName = (name + "_" + System.nanoTime()).toLowerCase();
        EmbeddedPostgres pg = getInstance();

        try (Connection conn = adminConnection(pg, "postgres");
             Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE DATABASE " + dbName);
        } catch (SQLException e) {
            throw new IllegalStateException("No se pudo crear la base de test " + dbName, e);
        }

        try (Connection conn = adminConnection(pg, dbName);
             Statement stmt = conn.createStatement()) {
            stmt.execute("""
                    DO $$
                    BEGIN
                        IF NOT EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = '%s') THEN
                            CREATE ROLE %s LOGIN PASSWORD '%s';
                        END IF;
                    END
                    $$;
                    """.formatted(APP_ROLE, APP_ROLE, APP_ROLE_PASSWORD));
            stmt.execute("GRANT CONNECT ON DATABASE " + dbName + " TO " + APP_ROLE);
            stmt.execute("GRANT USAGE ON SCHEMA public TO " + APP_ROLE);
        } catch (SQLException e) {
            throw new IllegalStateException("No se pudo provisionar el rol de aplicación en " + dbName, e);
        }

        return jdbcUrl(pg, dbName);
    }

    /** Registra las propiedades dinámicas para un {@code @SpringBootTest} con los dos roles. */
    public static void registerDynamicProperties(DynamicPropertyRegistry registry, String dbNamePrefix) {
        String url = createDatabase(dbNamePrefix);
        registry.add("spring.datasource.url", () -> url);
        registry.add("spring.datasource.username", () -> APP_ROLE);
        registry.add("spring.datasource.password", () -> APP_ROLE_PASSWORD);
        // spring.flyway.url va explícita, no heredada: application.yml ya no
        // depende del fallback automático de Spring Boot (ver el comentario ahí
        // mismo) y acá pasa lo mismo — sin esto Flyway intentaría conectar a la
        // URL por defecto del .yml en vez de a esta base de test.
        registry.add("spring.flyway.url", () -> url);
        registry.add("spring.flyway.user", () -> ADMIN_USER);
        registry.add("spring.flyway.password", () -> ADMIN_PASSWORD);
        registry.add("carelink.admin-datasource.url", () -> url);
        registry.add("carelink.admin-datasource.username", () -> ADMIN_USER);
        registry.add("carelink.admin-datasource.password", () -> ADMIN_PASSWORD);
    }

    /** Los mismos pares clave=valor que {@link #registerDynamicProperties}, como argumentos {@code --}. */
    public static String[] appArgs(String dbNamePrefix) {
        String url = createDatabase(dbNamePrefix);
        return new String[] {
                "--spring.datasource.url=" + url,
                "--spring.datasource.username=" + APP_ROLE,
                "--spring.datasource.password=" + APP_ROLE_PASSWORD,
                "--spring.flyway.url=" + url,
                "--spring.flyway.user=" + ADMIN_USER,
                "--spring.flyway.password=" + ADMIN_PASSWORD,
                "--carelink.admin-datasource.url=" + url,
                "--carelink.admin-datasource.username=" + ADMIN_USER,
                "--carelink.admin-datasource.password=" + ADMIN_PASSWORD,
        };
    }

    public static JdbcTemplate adminJdbcTemplate(String url) {
        return new JdbcTemplate(dataSource(url, ADMIN_USER, ADMIN_PASSWORD));
    }

    public static JdbcTemplate appJdbcTemplate(String url) {
        return new JdbcTemplate(dataSource(url, APP_ROLE, APP_ROLE_PASSWORD));
    }

    private static DataSource dataSource(String url, String user, String password) {
        return org.springframework.boot.jdbc.DataSourceBuilder.create()
                .url(url).username(user).password(password)
                .driverClassName("org.postgresql.Driver")
                .build();
    }

    private static String jdbcUrl(EmbeddedPostgres pg, String dbName) {
        return "jdbc:postgresql://localhost:" + pg.getPort() + "/" + dbName;
    }

    private static Connection adminConnection(EmbeddedPostgres pg, String dbName) throws SQLException {
        return DriverManager.getConnection(jdbcUrl(pg, dbName), ADMIN_USER, ADMIN_PASSWORD);
    }
}
