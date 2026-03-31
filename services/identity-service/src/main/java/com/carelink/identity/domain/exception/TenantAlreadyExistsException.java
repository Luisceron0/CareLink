package com.carelink.identity.domain.exception;

/** Indicates tenant slug already exists. */
public class TenantAlreadyExistsException extends RuntimeException {

    /**
     * Builds exception.
     *
     * @param message error message
     */
    public TenantAlreadyExistsException(final String message) {
        super(message);
    }
}
