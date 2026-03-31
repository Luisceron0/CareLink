package com.carelink.identity.domain.exception;

/** Indicates email must be verified before operation. */
public class EmailNotVerifiedException extends RuntimeException {

    /**
     * Builds exception.
     *
     * @param message error message
     */
    public EmailNotVerifiedException(final String message) {
        super(message);
    }
}
