package com.carelink.identity.infrastructure.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Comportamiento del hash de refresh tokens.
 *
 * <p>Complementa a {@code SecretConfigurationGuardTest}, que cubre la CONFIGURACIÓN del
 * secreto (ausente o por defecto → rechazado, hallazgo H-01 de la auditoría de
 * Sub-fase 8). Acá se fija lo otro: que el hash haga lo que la tabla {@code sessions}
 * necesita que haga. Se separan a propósito para que un fallo diga cuál de las dos
 * propiedades se rompió.
 */
class TokenHasherBehaviourTest {

    @Test
    @DisplayName("el mismo token da siempre el mismo hash — sin esto, un refresh válido no se reconocería")
    void hashIsDeterministic() {
        assertThat(TokenHasher.hash("un-refresh-token")).isEqualTo(TokenHasher.hash("un-refresh-token"));
    }

    @Test
    @DisplayName("el hash no contiene el token en claro — es el motivo entero de hashearlo")
    void hashDoesNotLeakTheToken() {
        String token = "token-de-prueba-reconocible";
        assertThat(TokenHasher.hash(token)).doesNotContain(token);
    }

    @Test
    @DisplayName("dos tokens distintos no colisionan")
    void differentTokensHashDifferently() {
        assertThat(TokenHasher.hash("token-a")).isNotEqualTo(TokenHasher.hash("token-b"));
    }

    @Test
    @DisplayName("el hash es Base64 URL-safe sin padding: viaja en una URL sin romperse")
    void hashIsUrlSafe() {
        String h = TokenHasher.hash("cualquier-token");
        assertThat(h).doesNotContain("=").doesNotContain("+").doesNotContain("/");
    }

    @Test
    @DisplayName("el mínimo de longitud del secreto está declarado como constante, no suelto en un if")
    void minimumSecretLengthIsDeclared() {
        assertThat(TokenHasher.MINIMUM_SECRET_LENGTH).isGreaterThanOrEqualTo(16);
    }
}
