package com.carelink.identity.domain.exception;

/** Indicates account is locked. */
public class AccountLockedException extends RuntimeException {

    /**
     * Builds exception.
     *
     * @param message error message
     */
    public AccountLockedException(final String message) {
        super(message);
    }
}
