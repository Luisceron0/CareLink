package com.carelink.identity.domain.value;

import java.util.Objects;

public final class Email {

    /** Canonicalized email value. */
    private final String value;

    /**
     * Builds validated email value object.
     *
     * @param emailValue raw email
     */
    public Email(final String emailValue) {
        if (emailValue == null
                || !emailValue.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            throw new IllegalArgumentException("Invalid email");
        }
        this.value = emailValue.toLowerCase();
    }

    /**
     * Returns canonical value.
     *
     * @return email value
     */
    public String value() {
        return value;
    }

    @Override
    public boolean equals(final Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Email)) {
            return false;
        }
        final Email email = (Email) other;
        return value.equals(email.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
