package com.carelink.identity.infrastructure.encryption;

import com.carelink.identity.domain.value.TenantSlug;
import com.carelink.identity.infrastructure.provisioning.PostgresSchemaProvisioner;
import com.carelink.identity.support.EmbeddedPostgresSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AC-09, contra PostgreSQL real — no una aserción sobre el algoritmo en aislamiento
 * (eso lo cubre {@link AesGcmEncryptionServiceTest}), sino la afirmación completa: un
 * valor cifrado, insertado por el rol de aplicación, leído con un SELECT directo, no es
 * texto plano.
 *
 * <p>Usa {@code tenant_<slug>.patients.full_name} — la tabla placeholder de la Sub-fase
 * 0/1, todavía sin la entidad Patient real (FR-CLN-01, próximo paso de esta sub-fase).
 * Lo que este test evidencia es el comportamiento de la columna cifrada en sí, que no
 * depende de que el resto del dominio Patient ya exista.
 */
class PhiColumnCannotBeReadAsPlaintextIT {

    @Test
    @DisplayName("AC-09 — SELECT directo sobre una columna PHI cifrada no devuelve texto plano")
    void directSelectOnEncryptedColumnDoesNotReturnPlaintext() {
        String url = EmbeddedPostgresSupport.createDatabase("ac09");
        JdbcTemplate admin = EmbeddedPostgresSupport.adminJdbcTemplate(url);
        JdbcTemplate app = EmbeddedPostgresSupport.appJdbcTemplate(url);

        new PostgresSchemaProvisioner(admin, new DefaultResourceLoader(), EmbeddedPostgresSupport.APP_ROLE)
                .provisionSchema(new TenantSlug("ac09tenant"));

        AesGcmEncryptionService encryption =
                new AesGcmEncryptionService(EmbeddedPostgresSupport.TEST_CLINIC_ENCRYPTION_KEY);
        String plaintext = "Juan Pérez — documento 123456789";
        String encrypted = encryption.encrypt(plaintext, "ac09tenant");

        // El rol de aplicación escribe el valor YA cifrado — EncryptionService cifra
        // antes de que el dato le llegue a JDBC, nunca al revés.
        app.update("INSERT INTO tenant_ac09tenant.patients (full_name) VALUES (?)", encrypted);

        String storedRaw = admin.queryForObject(
                "SELECT full_name FROM tenant_ac09tenant.patients", String.class);

        assertThat(storedRaw)
                .as("un SELECT directo sobre la columna no debe devolver el texto plano")
                .isNotEqualTo(plaintext)
                .doesNotContain("Juan")
                .doesNotContain("Pérez")
                .doesNotContain("123456789");

        // Y el valor guardado sigue siendo el dato correcto, no basura irrecuperable.
        assertThat(encryption.decrypt(storedRaw, "ac09tenant")).isEqualTo(plaintext);
    }
}
