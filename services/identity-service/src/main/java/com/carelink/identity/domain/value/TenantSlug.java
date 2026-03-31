package com.carelink.identity.domain.value;

import java.util.Objects;

public final class TenantSlug {

    /** Tenant slug value. */
    private final String value;

    /**
     * Builds validated tenant slug.
     *
     * @param slugValue raw slug
     */
    public TenantSlug(final String slugValue) {
        if (slugValue == null || !slugValue.matches("^[a-z0-9-]{3,64}$")) {
            throw new IllegalArgumentException("Invalid tenant slug");
        }
        this.value = slugValue;
    }

    /**
     * Returns slug value.
     *
     * @return slug value
     */
    public String value() {
        return value;
    }

    @Override
    public boolean equals(final Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof TenantSlug)) {
            return false;
        }
        final TenantSlug that = (TenantSlug) other;
        return value.equals(that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }
}
