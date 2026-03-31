package com.carelink.clinical.domain;

import com.carelink.clinical.domain.value.ICD10Code;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * Prescripción asociada a un encuentro clínico.
 *
 * @param id identificador
 * @param encounterId encuentro clínico
 * @param diagnosisCode código diagnóstico
 * @param medicationName nombre de medicamento
 * @param dosage dosis
 */
public record Prescription(UUID id,
                           UUID encounterId,
                           ICD10Code diagnosisCode,
                           String medicationName,
                           String dosage) {

    /**
     * Constructor canónico.
     */
    public Prescription {
        Objects.requireNonNull(id);
        Objects.requireNonNull(encounterId);
        Objects.requireNonNull(diagnosisCode);
        Objects.requireNonNull(medicationName);
        Objects.requireNonNull(dosage);
    }

    /**
     * Evalúa conflicto con alergias sin bloquear la prescripción.
     *
     * @param allergies alergias conocidas del paciente
     * @return resultado con warnings si hay conflicto
     */
    public PrescriptionEvaluation evaluateAgainstAllergies(
            final List<Allergy> allergies
    ) {
        Objects.requireNonNull(allergies);
        final String medication = medicationName.toLowerCase(Locale.ROOT);
        final List<String> warnings = allergies.stream()
                .map(Allergy::substance)
                .filter(Objects::nonNull)
                .filter(s -> medication.contains(s.toLowerCase(Locale.ROOT)))
                .map(s -> "Potential allergy conflict with: " + s)
                .toList();
        return new PrescriptionEvaluation(this, warnings);
    }

    /**
     * Resultado de evaluación de una prescripción.
     *
     * @param prescription prescripción evaluada
     * @param warnings advertencias encontradas
     */
    public record PrescriptionEvaluation(Prescription prescription,
                                         List<String> warnings) {
    }
}
