package com.carelink.clinical.infrastructure.encryption;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AesGcmEncryptionServiceTest {

    // Distinta de la usada en otros tests a propósito — no hay relación entre ellas
    // que un test deba asumir.
    private static final String MASTER_KEY = "MTIzNDU2Nzg5MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTI=";

    private final AesGcmEncryptionService service = new AesGcmEncryptionService(MASTER_KEY);

    @Test
    @DisplayName("round-trip: decrypt(encrypt(x)) == x")
    void roundTrip() {
        String plaintext = "Juan Pérez — documento 123456789";
        String stored = service.encrypt(plaintext, "tenant-a");
        assertThat(service.decrypt(stored, "tenant-a")).isEqualTo(plaintext);
    }

    @Test
    @DisplayName("el valor almacenado no contiene el texto plano en ninguna forma reconocible")
    void storedValueDoesNotLeakPlaintext() {
        String plaintext = "Juan Pérez";
        String stored = service.encrypt(plaintext, "tenant-a");

        assertThat(stored).doesNotContain(plaintext);
        assertThat(Base64.getDecoder().decode(stored)).isNotEqualTo(plaintext.getBytes());
    }

    @Test
    @DisplayName("dos cifrados del mismo texto plano producen salidas distintas — IV aleatorio por operación (§8.3)")
    void samePlaintextEncryptsDifferentlyEachTime() {
        String plaintext = "mismo valor";
        String first = service.encrypt(plaintext, "tenant-a");
        String second = service.encrypt(plaintext, "tenant-a");

        assertThat(first).isNotEqualTo(second);
        // Pero ambos decodifican al mismo texto plano — la aleatoriedad está en el IV,
        // no en un resultado indeterminista.
        assertThat(service.decrypt(first, "tenant-a")).isEqualTo(plaintext);
        assertThat(service.decrypt(second, "tenant-a")).isEqualTo(plaintext);
    }

    @Test
    @DisplayName("clave por tenant: el mismo texto cifrado para un tenant no decodifica bajo el slug de otro")
    void differentTenantsHaveDifferentDerivedKeys() {
        String stored = service.encrypt("dato sensible", "tenant-a");

        assertThatThrownBy(() -> service.decrypt(stored, "tenant-b"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("un valor alterado falla la verificación de GCM en vez de decodificar a basura silenciosamente")
    void tamperedCiphertextFailsAuthentication() {
        String stored = service.encrypt("dato sensible", "tenant-a");
        byte[] raw = Base64.getDecoder().decode(stored);
        raw[raw.length - 1] ^= 0x01; // flip del último bit del tag de autenticación
        String tampered = Base64.getEncoder().encodeToString(raw);

        assertThatThrownBy(() -> service.decrypt(tampered, "tenant-a"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("sin CLINIC_ENCRYPTION_KEY, el servicio no se construye — no cifra con una clave vacía")
    void refusesToConstructWithoutMasterKey() {
        assertThatThrownBy(() -> new AesGcmEncryptionService(""))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new AesGcmEncryptionService(null))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("una clave maestra que no decodifica a 32 bytes se rechaza al construir, no al cifrar")
    void refusesMasterKeyOfWrongLength() {
        String tooShort = Base64.getEncoder().encodeToString(new byte[16]);
        assertThatThrownBy(() -> new AesGcmEncryptionService(tooShort))
                .isInstanceOf(IllegalStateException.class);
    }
}
