package com.carelink.clinical.infrastructure.encryption;

import com.carelink.clinical.domain.port.EncryptionService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/**
 * AES-256-GCM, IV aleatorio por operación, clave por tenant — ADR-003, SRS §8.3.
 *
 * <p><b>Cómo se deriva la clave por tenant, decisión no cerrada del todo por el SRS:</b>
 * §8.3 pide "clave por tenant" pero no especifica un mecanismo de almacenamiento — no
 * existe todavía (ni está en el alcance de esta tarea construir) un Vault por tenant
 * para material de cifrado, y {@code .env.example} declara una sola
 * {@code CLINIC_ENCRYPTION_KEY} global. La resolución acá: esa variable es una clave
 * MAESTRA de alta entropía (256 bits, generada fuera del repo — igual que hoy), y la
 * clave real de cada tenant se DERIVA de la maestra vía HMAC-SHA256 sobre el slug del
 * tenant ({@code claveTenant = HMAC-SHA256(maestra, "carelink-phi:" + slug)}). Esto
 * cumple la propiedad que §8.3 pide (tenants distintos, claves distintas — filtrar la
 * clave derivada de un tenant no compromete la de otro) sin inventar un segundo
 * mecanismo de almacenamiento de secretos. Es una decisión de diseño razonable pero no
 * la única posible; si un milestone futuro agrega gestión de secretos por tenant, este
 * es el punto a reemplazar.
 *
 * <p>No es HKDF completo (RFC 5869) — se salta el paso de "Extract", que existe para
 * extraer entropía de material de entrada potencialmente débil (una contraseña, por
 * ejemplo). {@code CLINIC_ENCRYPTION_KEY} ya se asume de alta entropía (256 bits
 * generados con un CSPRNG, no una frase elegida por una persona), así que usar HMAC
 * directamente como función de expansión es válido y evita agregar una dependencia
 * (Bouncy Castle) solo por el paso de Extract que acá no hace falta.
 */
@Component
public class AesGcmEncryptionService implements EncryptionService {

    private static final String CIPHER_ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_TAG_BITS = 128;
    private static final int IV_BYTES = 12; // 96 bits — §8.3

    private final byte[] masterKey;
    private final SecureRandom secureRandom;

    public AesGcmEncryptionService(@Value("${carelink.clinic-encryption-key:}") String masterKeyBase64) {
        if (masterKeyBase64 == null || masterKeyBase64.isBlank()) {
            throw new IllegalStateException(
                    "CLINIC_ENCRYPTION_KEY no está configurada. Generarla fuera del repo — nunca versionada (§8.5).");
        }
        this.masterKey = Base64.getDecoder().decode(masterKeyBase64);
        if (this.masterKey.length != 32) {
            throw new IllegalStateException(
                    "CLINIC_ENCRYPTION_KEY debe decodificar a 32 bytes (AES-256); decodificó a "
                            + this.masterKey.length);
        }
        try {
            // Una sola instancia reusada entre operaciones — lo que tiene que ser
            // aleatorio POR OPERACIÓN es el IV (nextBytes por llamada, más abajo), no la
            // instancia de SecureRandom en sí. Reconstruirla en cada encrypt() solo
            // agregaría acceso repetido a la fuente de entropía del sistema sin ganar
            // nada: SecureRandom es thread-safe para nextBytes() concurrente.
            this.secureRandom = SecureRandom.getInstanceStrong();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Sin proveedor de SecureRandom fuerte disponible en esta JVM", e);
        }
    }

    @Override
    public String encrypt(String plaintext, String tenantSlug) {
        try {
            SecretKeySpec tenantKey = deriveTenantKey(tenantSlug);
            byte[] iv = new byte[IV_BYTES];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, tenantKey, new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] ciphertextAndTag = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            byte[] stored = new byte[iv.length + ciphertextAndTag.length];
            System.arraycopy(iv, 0, stored, 0, iv.length);
            System.arraycopy(ciphertextAndTag, 0, stored, iv.length, ciphertextAndTag.length);
            return Base64.getEncoder().encodeToString(stored);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("No se pudo cifrar el valor", e);
        }
    }

    @Override
    public String decrypt(String stored, String tenantSlug) {
        try {
            SecretKeySpec tenantKey = deriveTenantKey(tenantSlug);
            byte[] raw = Base64.getDecoder().decode(stored);
            if (raw.length < IV_BYTES) {
                throw new IllegalArgumentException("Valor cifrado demasiado corto para contener un IV válido");
            }
            byte[] iv = Arrays.copyOfRange(raw, 0, IV_BYTES);
            byte[] ciphertextAndTag = Arrays.copyOfRange(raw, IV_BYTES, raw.length);

            Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, tenantKey, new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] plaintext = cipher.doFinal(ciphertextAndTag);
            return new String(plaintext, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("No se pudo descifrar el valor — clave incorrecta o dato alterado", e);
        }
    }

    private SecretKeySpec deriveTenantKey(String tenantSlug) throws GeneralSecurityException {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(masterKey, "HmacSHA256"));
        byte[] derived = mac.doFinal(("carelink-phi:" + tenantSlug).getBytes(StandardCharsets.UTF_8));
        return new SecretKeySpec(derived, "AES");
    }
}
