package com.carelink.identity.domain.value;

import java.util.Objects;
import java.util.regex.Pattern;

public final class TenantSlug {

    /**
     * Única fuente de verdad del formato de slug. Pública a propósito: un sink que
     * necesite revalidar en profundidad (ADR-010 — "el tipo no evita que alguien
     * construya un TenantSlug con un regex distinto en otro punto del código") debe
     * reusar ESTE patrón, no copiar el regex a mano en otro archivo. Dos copias del
     * mismo regex es exactamente la clase de drift que la lección de ADR-010 señala.
     */
    public static final Pattern PATTERN = Pattern.compile("^[a-z0-9-]{3,64}$");

    private final String value;

    public TenantSlug(String value) {
        if (value == null || !PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException("Invalid tenant slug");
        }
        this.value = value;
    }

    public String value() { return value; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TenantSlug)) return false;
        TenantSlug that = (TenantSlug) o;
        return value.equals(that.value);
    }

    @Override
    public int hashCode() { return Objects.hash(value); }
}
