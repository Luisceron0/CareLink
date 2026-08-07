package com.carelink.identity.infrastructure.security;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * HMAC-SHA256 de los refresh tokens antes de guardarlos: leer la tabla {@code sessions}
 * no debe alcanzar para robar una sesión.
 *
 * <p><b>Hallazgo de la auditoría de Sub-fase 8, corregido acá.</b> Hasta ese momento
 * esta clase hacía {@code getenv().getOrDefault("REFRESH_TOKEN_HMAC_SECRET",
 * "dev-refresh-secret")}: sin la variable de entorno la aplicación arrancaba igual y
 * hasheaba TODOS los refresh tokens con un secreto escrito en el repositorio. Cualquiera
 * con acceso al código podía calcular el hash de un token y —con lectura sobre
 * {@code sessions}— reconocer o falsificar sesiones. Es el mismo defecto que ADR-010
 * eliminó con el fallback {@code dev-secret} del gateway Python, reaparecido en otro
 * archivo con otro nombre, y NO detectado por el gate de CI de AC-04: ese gate busca la
 * cadena literal {@code dev-secret}, que no matchea {@code dev-refresh-secret}.
 *
 * <p>La corrección es la misma que ya se aplicaba a {@code CLINIC_ENCRYPTION_KEY} en
 * {@code AesGcmEncryptionService}: sin secreto configurado, la aplicación NO ARRANCA. Un
 * default permisivo convierte un control de seguridad en un adorno — y en uno que
 * además parece estar puesto. Vale más un arranque fallido y ruidoso que una aplicación
 * funcionando con criptografía conocida.
 *
 * <p>La validación vive en un campo estático, así que dispara al cargar la clase — en la
 * práctica, en el primer login o refresh y no en el arranque. {@code RefreshSecretGuard}
 * la adelanta al arranque para que el problema no espere al primer usuario.
 */
public final class TokenHasher {
    private static final String HMAC_ALGO = "HmacSHA256";

    /** Un secreto de 8 caracteres no es un secreto: es adivinable por fuerza bruta. */
    static final int MINIMUM_SECRET_LENGTH = 16;

    private static final String SECRET = requireSecret();

    private TokenHasher() {}

    static String requireSecret() {
        String configured = System.getenv("REFRESH_TOKEN_HMAC_SECRET");
        if (configured == null || configured.isBlank()) {
            throw new IllegalStateException(
                    "REFRESH_TOKEN_HMAC_SECRET no está configurado. Los refresh tokens se guardan "
                            + "hasheados con este secreto; sin él la aplicación no arranca — antes caía a un "
                            + "valor por defecto escrito en el repositorio, o sea una credencial pública "
                            + "(ver ADR-010 y el hallazgo de la auditoría de Sub-fase 8).");
        }
        if (configured.length() < MINIMUM_SECRET_LENGTH) {
            throw new IllegalStateException(
                    "REFRESH_TOKEN_HMAC_SECRET tiene " + configured.length() + " caracteres; el mínimo es "
                            + MINIMUM_SECRET_LENGTH + ".");
        }
        return configured;
    }

    public static String hash(String token) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGO);
            mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), HMAC_ALGO));
            byte[] h = mac.doFinal(token.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(h);
        } catch (Exception e) {
            throw new RuntimeException("Unable to hash token", e);
        }
    }
}
