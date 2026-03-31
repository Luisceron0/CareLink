package com.carelink.identity.domain.exception;

/** Indicates tax id has invalid format. */
public class InvalidTaxIdException extends RuntimeException {

    /**
     * Builds exception.
     *
     * @param message error message
     */
    public InvalidTaxIdException(final String message) {
        super(message);
    }
}
