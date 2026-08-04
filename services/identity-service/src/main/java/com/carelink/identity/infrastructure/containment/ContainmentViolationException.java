package com.carelink.identity.infrastructure.containment;

/**
 * Se lanza cuando una garantía de contención de §1.6 no se cumple.
 *
 * <p>Extiende {@link IllegalStateException} para que el fallo ocurra durante la
 * inicialización del contexto de Spring: el proceso no llega a atender una sola
 * petición. No se captura en ningún lado — no hay recuperación posible de "esta base
 * podría tener datos reales", solo detención.
 */
public class ContainmentViolationException extends IllegalStateException {

    public ContainmentViolationException(String message) {
        super(message);
    }

    public ContainmentViolationException(String message, Throwable cause) {
        super(message, cause);
    }
}
