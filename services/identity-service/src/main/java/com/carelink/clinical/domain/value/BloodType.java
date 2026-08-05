package com.carelink.clinical.domain.value;

/** FR-CLN-01. {@code UNKNOWN} es un valor legítimo, no la ausencia de uno — un paciente
 * cuyo tipo de sangre no se ha determinado todavía no es lo mismo que un campo vacío. */
public enum BloodType {
    A_POSITIVE, A_NEGATIVE,
    B_POSITIVE, B_NEGATIVE,
    AB_POSITIVE, AB_NEGATIVE,
    O_POSITIVE, O_NEGATIVE,
    UNKNOWN
}
