package com.carelink.clinical.infrastructure.encryption;

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
 * <p>Usa {@code tenant_<slug>.patients.full_name} directo por SQL, sin pasar por
 * {@code Patient}/{@code JdbcPatientRepository} — a propósito: {@code PatientLifecycleIT}
 * ya cubre el flujo real de punta a punta; esto queda como la prueba mínima e
 * independiente de que la combinación cifrado+columna funciona, sin depender de que el
 * resto de la capa de dominio esté bien cableada.
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
        // antes de que el dato le llegue a JDBC, nunca al revés. Las demás columnas
        // NOT NULL de patients no son PHI en foco de este test — valores mínimos
        // válidos, sin cifrar, para satisfacer el esquema.
        app.update("INSERT INTO tenant_ac09tenant.patients " +
                        "(full_name, document_type, document_number, date_of_birth, sex, blood_type) " +
                        "VALUES (?, 'CEDULA_CIUDADANIA', ?, ?, 'UNKNOWN', 'UNKNOWN')",
                encrypted, encryption.encrypt("000000", "ac09tenant"),
                encryption.encrypt(java.time.LocalDate.of(2000, 1, 1).toString(), "ac09tenant"));

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
