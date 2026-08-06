package com.carelink.identity.integration;

import com.carelink.identity.infrastructure.security.TokenHasher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Regresión del hallazgo principal de la auditoría de Sub-fase 8.
 *
 * <p>{@code TokenHasher} tenía
 * {@code getenv().getOrDefault("REFRESH_TOKEN_HMAC_SECRET", "dev-refresh-secret")}: sin
 * la variable de entorno, la aplicación arrancaba igual y hasheaba todos los refresh
 * tokens con un secreto escrito en el repositorio. Este test existe para que ese default
 * no pueda volver — un gate de CI lo detecta por texto, y esto lo detecta por
 * comportamiento, que es lo que realmente importa.
 *
 * <p>Se invoca {@code requireSecret()} por reflexión y no se lee el campo estático
 * {@code SECRET} porque el campo se inicializa una sola vez al cargar la clase, con el
 * entorno que hubiera en ese momento; llamar al método permite ejercitar la validación
 * misma sin depender de en qué orden se cargaron las clases del test.
 */
class SecretConfigurationGuardTest {

    @Test
    @DisplayName("sin REFRESH_TOKEN_HMAC_SECRET la validación FALLA — no cae a un default")
    void missingSecretIsRejectedRatherThanDefaulted() throws Exception {
        Method requireSecret = TokenHasher.class.getDeclaredMethod("requireSecret");
        requireSecret.setAccessible(true);

        String configured = System.getenv("REFRESH_TOKEN_HMAC_SECRET");

        if (configured == null || configured.isBlank()) {
            // Entorno sin la variable: la validación tiene que rechazar, no inventar
            // un secreto.
            assertThatThrownBy(() -> requireSecret.invoke(null))
                    .hasRootCauseInstanceOf(IllegalStateException.class)
                    .rootCause()
                    .hasMessageContaining("REFRESH_TOKEN_HMAC_SECRET");
        } else {
            // Entorno con la variable (el caso de CI y compose): la validación acepta y
            // devuelve exactamente lo configurado — sin sustituirlo ni completarlo.
            assertThat(requireSecret.invoke(null)).isEqualTo(configured);
            assertThat(configured.length())
                    .as("un secreto por debajo del mínimo se rechazaría al arrancar")
                    .isGreaterThanOrEqualTo(16);
        }
    }

    @Test
    @DisplayName("el código fuente no contiene ningún default de secreto (el defecto original)")
    void sourceHasNoHardcodedSecretFallback() throws Exception {
        // Lee el fuente y no el bytecode: el defecto era textual —un literal como valor
        // por defecto— y es en el fuente donde alguien lo reintroduciría.
        java.nio.file.Path source = java.nio.file.Path.of(
                "src/main/java/com/carelink/identity/infrastructure/security/TokenHasher.java");
        String raw = java.nio.file.Files.readString(source);

        // Se quitan los comentarios ANTES de buscar. El javadoc de TokenHasher CITA el
        // patrón defectuoso para explicar el hallazgo, y sin este paso el test fallaba
        // contra su propia documentación — una falla real la primera vez que corrió.
        // La regla es sobre lo que el código HACE, no sobre lo que la prosa menciona:
        // si el test no distinguiera esos dos, la única forma de tenerlo en verde sería
        // dejar de explicar el defecto, que es justo lo contrario de lo que se quiere.
        String code = raw
                .replaceAll("(?s)/\\*.*?\\*/", "")   // bloques y javadoc
                .replaceAll("(?m)//.*$", "");        // comentarios de línea

        assertThat(code)
                .as("el secreto no debe leerse con valor por defecto")
                .doesNotContain("getOrDefault(\"REFRESH_TOKEN_HMAC_SECRET\"");
    }
}
