package com.carelink.clinical.application;

import com.carelink.clinical.domain.Allergy;
import com.carelink.clinical.domain.Prescription;

import java.util.List;
import java.util.Objects;

/**
 * Caso de uso para evaluar conflictos de alergia en prescripciones.
 */
public final class EvaluatePrescriptionUseCase {

    /**
     * Evalúa prescripción contra alergias y retorna advertencias sin bloquear.
     *
     * @param prescription prescripción
     * @param allergies alergias del paciente
     * @return evaluación con advertencias
     */
    public Prescription.PrescriptionEvaluation evaluate(
            final Prescription prescription,
            final List<Allergy> allergies) {
        Objects.requireNonNull(prescription);
        Objects.requireNonNull(allergies);
        return prescription.evaluateAgainstAllergies(allergies);
    }
}
