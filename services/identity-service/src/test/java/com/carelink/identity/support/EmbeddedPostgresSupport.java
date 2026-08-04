package com.carelink.identity.support;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;

import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * PostgreSQL embebido compartido por los tests que necesitan una base real.
 *
 * <p>Una sola instancia por ejecución de la JVM: arrancar Postgres cuesta segundos, y
 * cada test crea su propia base dentro de esa instancia, así que quedan aislados entre
 * sí sin pagar el arranque más de una vez.
 *
 * <p>Se usa Postgres de verdad, no H2 en modo compatibilidad, porque lo que estos tests
 * verifican es específico del motor: triggers, GRANTs y constraints. Un doble que
 * "se parece a Postgres" no puede evidenciar que el usuario de aplicación no tiene
 * permiso de DELETE (AC-10).
 */
public final class EmbeddedPostgresSupport {

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

    /** Crea una base vacía con nombre único y devuelve su URL JDBC. */
    public static String createDatabase(String name) {
        String dbName = (name + "_" + System.nanoTime()).toLowerCase();
        EmbeddedPostgres pg = getInstance();
        try (var conn = pg.getPostgresDatabase().getConnection();
             var stmt = conn.createStatement()) {
            stmt.execute("CREATE DATABASE " + dbName);
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo crear la base de test " + dbName, e);
        }
        return "jdbc:postgresql://localhost:" + pg.getPort() + "/" + dbName
                + "?user=postgres&password=postgres";
    }

    public static int getPort() {
        return getInstance().getPort();
    }
}
