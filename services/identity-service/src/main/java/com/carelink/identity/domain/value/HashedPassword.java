package com.carelink.identity.domain.value;

import java.util.Objects;

public final class HashedPassword {
    private final String value;

    public HashedPassword(String value) {
        if (value == null || value.isEmpty()) {
            throw new IllegalArgumentException("Invalid hashed password");
        }
        this.value = value;
    }

    public String value() { return value; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof HashedPassword)) return false;
        HashedPassword that = (HashedPassword) o;
        return value.equals(that.value);
    }

    @Override
    public int hashCode() { return Objects.hash(value); }
}
