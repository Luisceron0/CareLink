package com.carelink.clinical.domain.exception;

/**
 * Excepción para registros clínicos inmutables luego de firma.
 */
public final class ImmutableRecordException extends RuntimeException {

    /**
     * Constructor.
     *
     * @param code código del error
     */
    public ImmutableRecordException(final String code) {
        super(code);
    }
}
