package com.carelink.clinical.domain;

/**
 * FR-CLN-12 — "los avisos de conflicto advierten, NUNCA bloquean".
 *
 * <p>Eso es una decisión clínica deliberada, no una comodidad: un sistema que bloquea
 * una prescripción por un conflicto detectado automáticamente le quita al médico una
 * decisión que puede tener razones que el sistema no conoce (una alergia registrada que
 * resultó ser intolerancia leve, una interacción aceptable frente a la alternativa). El
 * sistema informa; el criterio clínico decide. Por eso esto es un record que se DEVUELVE
 * junto con la prescripción ya creada, y no una excepción que la impida.
 */
public record PrescriptionConflict(Type type, String detail) {

    public enum Type {
        /** El paciente tiene registrada una alergia que coincide con el medicamento o su clase. */
        ALLERGY,
        /** Ya hay una prescripción activa de la misma clase farmacológica. */
        ACTIVE_SAME_CLASS
    }
}
