package com.carelink.identity.infrastructure.containment;

import com.carelink.identity.Application;
import com.carelink.identity.support.EmbeddedPostgresSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.assertj.core.api.Assertions.fail;

/**
 * AC-01 y AC-02 — las garantías de contención del SRS §1.6, verificadas arrancando la
 * aplicación de verdad y comprobando que se niega a arrancar.
 *
 * <p>No alcanza con testear que {@code DemoModeGuard} lanza una excepción: lo que el
 * criterio de aceptación afirma es que <em>el arranque falla</em>. Por eso cada test
 * construye la aplicación completa con {@link SpringApplicationBuilder} y verifica que
 * {@code run()} propaga el fallo, en vez de invocar el guard aisladamente.
 */
class ContainmentGuardIT {

    /**
     * Arranca la aplicación completa contra una base de test nueva, con los dos roles
     * (admin para Flyway, {@code carelink_app} restringido para el resto) ya
     * provisionados por {@link EmbeddedPostgresSupport#appArgs}.
     *
     * <p>Se arranca como aplicación web con {@code server.port=0} —puerto efímero— y
     * no con {@code WebApplicationType.NONE}, porque {@code SecurityConfig} declara un
     * {@code SecurityFilterChain} que necesita {@code HttpSecurity}, un bean que solo
     * existe en un contexto web. Sin servidor, el contexto falla por una razón que no
     * tiene nada que ver con la contención, y el test pasaría o fallaría por el motivo
     * equivocado.
     *
     * <p>La configuración extra va como argumentos de línea de comandos
     * ({@code --clave=valor}), no por {@code SpringApplicationBuilder.properties(...)}:
     * ese método registra <em>default properties</em>, la fuente de menor precedencia
     * de todas, así que {@code application.yml} las pisa y el test terminaría
     * verificando el comportamiento por defecto en lugar del configurado.
     */
    private ConfigurableApplicationContext run(String dbNamePrefix, String... extraProperties) {
        String[] base = EmbeddedPostgresSupport.appArgs(dbNamePrefix);
        String[] args = new String[base.length + extraProperties.length + 1];
        System.arraycopy(base, 0, args, 0, base.length);
        args[base.length] = "--server.port=0";
        for (int i = 0; i < extraProperties.length; i++) {
            args[base.length + 1 + i] = "--" + extraProperties[i];
        }

        return new SpringApplicationBuilder(Application.class).run(args);
    }

    /**
     * Verifica que el arranque falló por contención, y con el motivo esperado.
     *
     * <p>Recorre la cadena de causas en vez de mirar solo la raíz. La diferencia importa:
     * cuando el sello falta, {@code DemoModeGuard} envuelve la {@code PSQLException} de
     * "relation containment_marker does not exist", así que la causa <em>raíz</em> es la
     * de Postgres y no la de contención. Afirmar sobre la raíz haría fallar un test que
     * en realidad está describiendo el comportamiento correcto.
     */
    private void assertFailsContainment(ThrowingCallable boot, String expectedMessageFragment) {
        Throwable thrown = catchThrowable(boot);
        assertThat(thrown).as("se esperaba que el arranque fallara").isNotNull();

        for (Throwable t = thrown; t != null; t = t.getCause()) {
            if (t instanceof ContainmentViolationException) {
                assertThat(t.getMessage()).contains(expectedMessageFragment);
                return;
            }
            if (t.getCause() == t) break;
        }
        fail("El arranque falló, pero no por ContainmentViolationException. Causa: %s", thrown);
    }

    @Test
    @DisplayName("AC-01 — el arranque falla si DEMO_MODE no es true")
    void bootFailsWithoutDemoMode() {
        assertFailsContainment(
                () -> run("ac01", "carelink.demo-mode=false", "carelink.app-env=test"),
                "DEMO_MODE no es true");
    }

    @Test
    @DisplayName("AC-01 — el arranque falla también si DEMO_MODE simplemente no está definido")
    void bootFailsWhenDemoModeIsAbsent() {
        // Sin `carelink.demo-mode`: el default de application.yml es false, a propósito.
        // Un default permisivo haría que "olvidarse de configurar" sea el camino feliz.
        assertFailsContainment(
                () -> run("ac01absent", "carelink.app-env=test"),
                "DEMO_MODE no es true");
    }

    @Test
    @DisplayName("APP_ENV de producción hace fallar el arranque aunque DEMO_MODE sea true")
    void bootFailsOnProductionEnvironment() {
        assertFailsContainment(
                () -> run("acenv", "carelink.demo-mode=true", "carelink.app-env=production"),
                "entorno de producción");
    }

    @Test
    @DisplayName("AC-02 — el arranque falla contra una base sin el sello SYNTHETIC_DATA_ONLY")
    void bootFailsAgainstUnstampedDatabase() {
        // `flyway.target=1` aplica la línea base de Identity pero NO la V2 que inserta el
        // sello. Resultado: una base con esquema válido y sin sello — exactamente la
        // forma peligrosa, porque "las tablas están, arrancá tranquilo" es justo la
        // conclusión equivocada. Que las tablas existan no dice nada sobre qué datos
        // tienen adentro.
        assertFailsContainment(
                () -> run("ac02",
                        "carelink.demo-mode=true",
                        "carelink.app-env=test",
                        "spring.flyway.target=1"),
                "sello de datos sintéticos");
    }

    @Test
    @DisplayName("Con las tres condiciones satisfechas, la aplicación arranca")
    void bootSucceedsWhenContained() {
        // El contrapeso de los tests anteriores: sin esto, un guard que rechazara
        // absolutamente todo también los pasaría.
        try (ConfigurableApplicationContext ctx =
                     run("acok", "carelink.demo-mode=true", "carelink.app-env=test")) {
            assertThat(ctx.isRunning()).isTrue();
            assertThat(ctx.getBean(DemoModeGuard.class)).isNotNull();
        }
    }
}
