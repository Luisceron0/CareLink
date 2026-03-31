package com.carelink.clinical.domain.value;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Código ICD-10 validado.
 *
 * @param value valor del código
 */
public record ICD10Code(String value) {

    /** Patrón de validación para códigos ICD-10. */
    private static final Pattern ICD10_PATTERN =
            Pattern.compile("^[A-TV-Z][0-9][0-9AB](\\.[0-9A-TV-Z]{1,4})?$");

    /**
     * Constructor canónico.
     *
     * @param value código ICD-10
     */
    public ICD10Code {
        Objects.requireNonNull(value);
        final String normalized = value.trim().toUpperCase();
        if (!ICD10_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException("Invalid ICD10 code");
        }
        value = normalized;
    }
}
