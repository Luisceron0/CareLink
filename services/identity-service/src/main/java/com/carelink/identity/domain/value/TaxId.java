package com.carelink.identity.domain.value;

import java.util.Objects;

public final class TaxId {

    /** Canonical tax id value. */
    private final String value;

    /**
     * Builds validated tax id.
     *
     * @param taxIdValue raw tax id
     */
    public TaxId(final String taxIdValue) {
        if (taxIdValue == null || taxIdValue.trim().isEmpty()) {
            throw new IllegalArgumentException("Invalid tax id");
        }
        this.value = taxIdValue.trim();
    }

    /**
     * Returns tax id value.
     *
     * @return tax id
     */
    public String value() {
        return value;
    }

    @Override
    public boolean equals(final Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof TaxId)) {
            return false;
        }
        final TaxId taxId = (TaxId) other;
        return value.equals(taxId.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }
}
