package com.carelink.clinical.domain.value;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Documento de identidad validado por tipo.
 *
 * @param type tipo documental (CC, PASSPORT, NIT)
 * @param value valor normalizado
 */
public record DocumentId(String type, String value) {

    /** Patrón para cédula de ciudadanía. */
    private static final Pattern CC_PATTERN = Pattern.compile("^[0-9]{6,12}$");

    /** Patrón para pasaporte. */
    private static final Pattern PASSPORT_PATTERN =
            Pattern.compile("^[A-Z0-9]{6,12}$");

    /** Patrón para NIT con dígito de verificación opcional. */
    private static final Pattern NIT_PATTERN =
            Pattern.compile("^[0-9]{6,15}(-[0-9])?$");

    /**
     * Constructor canónico con validaciones.
     *
     * @param type tipo documental
     * @param value valor de documento
     */
    public DocumentId {
        Objects.requireNonNull(type);
        Objects.requireNonNull(value);

        final String normalizedType = type.trim().toUpperCase();
        final String normalizedValue = value.trim().toUpperCase();

        switch (normalizedType) {
            case "CC" -> validate(normalizedValue, CC_PATTERN, "Invalid CC");
            case "PASSPORT" -> validate(
                    normalizedValue,
                    PASSPORT_PATTERN,
                    "Invalid PASSPORT"
                );
            case "NIT" -> validate(normalizedValue, NIT_PATTERN, "Invalid NIT");
            default -> throw new IllegalArgumentException(
                    "Unsupported document type"
                );
        }

        type = normalizedType;
        value = normalizedValue;
    }

    private static void validate(final String value,
                                 final Pattern pattern,
                                 final String message) {
        if (!pattern.matcher(value).matches()) {
            throw new IllegalArgumentException(message);
        }
    }
}
