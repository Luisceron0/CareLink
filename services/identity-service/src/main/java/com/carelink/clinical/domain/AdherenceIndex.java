package com.carelink.clinical.domain;

import java.util.UUID;

/**
 * FR-CLN-12 — "índice de adherencia = dosis administradas / prescritas en un período".
 *
 * <p>{@code ratio} puede superar 1.0 y NO se recorta: si se dispensó más de lo prescrito,
 * eso es un dato clínico real —un error de dispensación, una prescripción modificada
 * fuera del sistema— y aplanarlo a 1.0 escondería justo el caso que vale la pena mirar.
 * Un índice que nunca puede pasar de 100% no distingue "adherencia perfecta" de
 * "se dispensó el doble".
 *
 * <p>{@code prescribedDoses == 0} da ratio indefinido, no cero: una prescripción sin
 * total de dosis registrado (el campo es opcional) no tiene adherencia calculable, y
 * reportar 0% diría "no tomó nada", que es una afirmación clínica distinta y falsa.
 */
public record AdherenceIndex(
        UUID patientId,
        UUID prescriptionId,
        int prescribedDoses,
        int dispensedDoses,
        Double ratio
) {
    public static AdherenceIndex of(UUID patientId, UUID prescriptionId, Integer prescribedDoses, int dispensedDoses) {
        int prescribed = prescribedDoses == null ? 0 : prescribedDoses;
        Double ratio = prescribed == 0 ? null : (double) dispensedDoses / prescribed;
        return new AdherenceIndex(patientId, prescriptionId, prescribed, dispensedDoses, ratio);
    }

    public boolean isCalculable() {
        return ratio != null;
    }
}
