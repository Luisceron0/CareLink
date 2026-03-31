package com.carelink.clinical.domain;

import com.carelink.clinical.application.EvaluatePrescriptionUseCase;
import com.carelink.clinical.domain.value.ICD10Code;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test de advertencia por conflicto alergia-prescripción.
 */
public class PrescriptionAllergyWarningTest {

    @Test
    void warnsOnAllergyConflictWithoutBlocking() {
        final Prescription prescription = new Prescription(
                UUID.randomUUID(),
                UUID.randomUUID(),
                new ICD10Code("J02.9"),
                "Amoxicillin 500mg",
                "Every 8 hours"
        );

        final List<Allergy> allergies = List.of(
                new Allergy(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        "amoxicillin",
                        "rash"
                )
        );

        final EvaluatePrescriptionUseCase useCase =
                new EvaluatePrescriptionUseCase();
        final Prescription.PrescriptionEvaluation evaluation =
                useCase.evaluate(prescription, allergies);

        assertFalse(evaluation.warnings().isEmpty());
        assertTrue(evaluation.warnings().get(0).contains("amoxicillin"));
    }
}
