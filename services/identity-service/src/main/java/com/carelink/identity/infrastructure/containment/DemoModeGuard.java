package com.carelink.identity.infrastructure.containment;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.DependsOn;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Set;

/**
 * Impide que la aplicación arranque fuera de un entorno de demostración.
 *
 * <p>CareLink es una implementación de referencia. No debe procesar información de
 * salud de personas reales ni desplegarse a producción bajo ninguna configuración
 * (SRS §1.6). Esta clase es la versión ejecutable de esa afirmación: sin ella, §1.6
 * es un párrafo en un documento.
 *
 * <p>Verifica tres condiciones, y falla el arranque si alguna no se cumple:
 * <ol>
 *   <li>{@code DEMO_MODE} es {@code true} — AC-01</li>
 *   <li>{@code APP_ENV} no es un valor de producción</li>
 *   <li>la base de datos lleva el sello {@code SYNTHETIC_DATA_ONLY} — AC-02</li>
 * </ol>
 *
 * <p>La tercera es la que importa y la razón de que las otras dos no alcancen: una
 * variable de entorno describe al proceso, no a la base a la que ese proceso se
 * conecta. Alguien puede arrancar con {@code DEMO_MODE=true} apuntando a una base de
 * producción sin darse cuenta — un {@code DATABASE_URL} mal copiado alcanza. El sello
 * viaja con la base, así que la pregunta que se responde es "¿esta base es de
 * juguete?" y no "¿el que arrancó esto creía que lo era?".
 *
 * <p><b>No tiene interruptor para apagarlo.</b> Un control de contención con una
 * propiedad {@code enabled=false} es un control que alguien va a desactivar para que
 * un test pase, y a partir de ahí no protege nada. Los tests que necesitan un
 * contexto sin base de datos no cargan esta clase; los que la cargan, la satisfacen.
 */
@Component
@DependsOn("flywayInitializer")
public class DemoModeGuard {

    /** Valores de APP_ENV que se consideran producción. Comparación case-insensitive. */
    private static final Set<String> PRODUCTION_LIKE =
            Set.of("prod", "production", "prd", "live", "staging", "stage", "pre-prod", "preprod");

    private static final String REQUIRED_STAMP = "SYNTHETIC_DATA_ONLY";

    private final JdbcTemplate jdbcTemplate;
    private final boolean demoMode;
    private final String appEnv;

    public DemoModeGuard(JdbcTemplate jdbcTemplate,
                         @Value("${carelink.demo-mode:false}") boolean demoMode,
                         @Value("${carelink.app-env:local}") String appEnv) {
        this.jdbcTemplate = jdbcTemplate;
        this.demoMode = demoMode;
        this.appEnv = appEnv;
    }

    @PostConstruct
    void verifyContainment() {
        verifyDemoModeEnabled();
        verifyEnvironmentIsNotProduction();
        verifyDatabaseCarriesSyntheticDataStamp();
    }

    /** AC-01. */
    private void verifyDemoModeEnabled() {
        if (!demoMode) {
            throw new ContainmentViolationException(
                    "DEMO_MODE no es true. CareLink es una implementación de referencia y no "
                    + "arranca fuera de modo demo (SRS §1.6). Si estás intentando desplegar esto "
                    + "a un entorno real, la respuesta correcta no es cambiar esta variable.");
        }
    }

    private void verifyEnvironmentIsNotProduction() {
        if (PRODUCTION_LIKE.contains(appEnv.trim().toLowerCase(Locale.ROOT))) {
            throw new ContainmentViolationException(
                    "APP_ENV='" + appEnv + "' es un entorno de producción. Este sistema no define "
                    + "ni admite un entorno de producción (SRS §1.6, §15.1).");
        }
    }

    /** AC-02. */
    private void verifyDatabaseCarriesSyntheticDataStamp() {
        String stamp;
        try {
            stamp = jdbcTemplate.queryForObject(
                    "SELECT stamp FROM containment_marker", String.class);
        } catch (Exception e) {
            throw new ContainmentViolationException(
                    "La base de datos no lleva el sello de datos sintéticos: no se pudo leer "
                    + "containment_marker. Una base sin sello se trata como base desconocida, y "
                    + "una base desconocida puede contener datos reales (SRS §15.2, AC-02).", e);
        }

        if (!REQUIRED_STAMP.equals(stamp)) {
            throw new ContainmentViolationException(
                    "El sello de la base es '" + stamp + "' y se esperaba '" + REQUIRED_STAMP + "'.");
        }
    }
}
