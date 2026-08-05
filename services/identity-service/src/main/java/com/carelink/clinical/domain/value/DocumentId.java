package com.carelink.clinical.domain.value;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Documento de identidad — FR-CLN-01. Validación básica de formato, no un validador
 * exhaustivo de documentos internacionales: cédula/tarjeta de identidad colombianas son
 * numéricas (6 a 10 dígitos, sin dígito de chequeo público — la Registraduría no publica
 * un algoritmo de validación), pasaporte es alfanumérico (formatos varían por país
 * emisor, así que se valida longitud y caracteres, no un formato específico).
 */
public final class DocumentId {
    private static final Pattern NUMERIC_DOCUMENT = Pattern.compile("^[0-9]{6,10}$");
    private static final Pattern PASSPORT = Pattern.compile("^[A-Za-z0-9]{6,12}$");

    private final DocumentType type;
    private final String number;

    public DocumentId(DocumentType type, String number) {
        if (type == null) {
            throw new IllegalArgumentException("DocumentType requerido");
        }
        if (number == null) {
            throw new IllegalArgumentException("Número de documento requerido");
        }
        String trimmed = number.trim();
        Pattern pattern = type == DocumentType.PASAPORTE ? PASSPORT : NUMERIC_DOCUMENT;
        if (!pattern.matcher(trimmed).matches()) {
            throw new IllegalArgumentException(
                    "Número de documento inválido para " + type + ": " + trimmed);
        }
        this.type = type;
        this.number = trimmed;
    }

    public DocumentType type() { return type; }
    public String number() { return number; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DocumentId that)) return false;
        return type == that.type && number.equals(that.number);
    }

    @Override
    public int hashCode() { return Objects.hash(type, number); }

    @Override
    public String toString() { return type + ":" + number; }
}
