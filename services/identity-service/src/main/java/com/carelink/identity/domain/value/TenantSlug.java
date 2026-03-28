package com.carelink.identity.domain.value;

import java.util.Objects;

public final class TenantSlug {
    private final String value;

    public TenantSlug(String value) {
        if (value == null || !value.matches("^[a-z0-9-]{3,64}$")) {
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
