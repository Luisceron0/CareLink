package com.carelink.identity.domain.value;

import java.util.Objects;

public final class HashedPassword {

    /** Hashed password value. */
    private final String value;

    /**
     * Builds validated hashed password.
     *
     * @param hashedValue hashed password
     */
    public HashedPassword(final String hashedValue) {
        if (hashedValue == null || hashedValue.isEmpty()) {
            throw new IllegalArgumentException("Invalid hashed password");
        }
        this.value = hashedValue;
    }

    /**
     * Returns hashed password value.
     *
     * @return hashed password
     */
    public String value() {
        return value;
    }

    @Override
    public boolean equals(final Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof HashedPassword)) {
            return false;
        }
        final HashedPassword that = (HashedPassword) other;
        return value.equals(that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }
}
