package com.carelink.identity.domain.value;

import java.util.Objects;

public final class TaxId {
    private final String value;

    public TaxId(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("Invalid tax id");
        }
        this.value = value.trim();
    }

    public String value() { return value; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TaxId)) return false;
        TaxId taxId = (TaxId) o;
        return value.equals(taxId.value);
    }

    @Override
    public int hashCode() { return Objects.hash(value); }
}
