package com.carelink.scheduling.domain.exception;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Excepción lanzada cuando no hay disponibilidad para el slot solicitado.
 *
 * El mensaje incluye exactamente ALTERNATIVE_COUNT alternativas, siempre
 * formateadas en la cadena.
 */
public final class SlotAlreadyBookedException extends RuntimeException {

    /** Número de alternativas incluidas en el mensaje. */
    private static final int ALTERNATIVE_COUNT = 3;

    /** Alternativas sugeridas para el slot en conflicto. */
    private final List<LocalDateTime> alternatives;

    /**
     * Constructor.
     *
     * @param alternativesArg lista de alternativas sugeridas
     *                     (puede tener menos de 3)
     */
    public SlotAlreadyBookedException(
            final List<LocalDateTime> alternativesArg
    ) {
        super(buildMessage(alternativesArg));
        this.alternatives = normalizeAlternatives(alternativesArg);
    }

    /**
     * Retorna alternativas sugeridas para reserva.
     *
     * @return lista inmutable con exactamente 3 elementos
     */
    public List<LocalDateTime> alternatives() {
        return alternatives;
    }

    private static String buildMessage(final List<LocalDateTime> alts) {
        // Garantizar exactamente ALTERNATIVE_COUNT alternativas en el mensaje
        final String[] out = new String[ALTERNATIVE_COUNT];
        for (int i = 0; i < ALTERNATIVE_COUNT; i++) {
            if (i < alts.size() && alts.get(i) != null) {
                out[i] = alts.get(i).toString();
            } else {
                out[i] = "";
            }
        }
        return "Slot already booked. Alternatives: "
            + String.join(", ", out);
    }

    private static List<LocalDateTime> normalizeAlternatives(
            final List<LocalDateTime> alts
    ) {
        final List<LocalDateTime> output = new ArrayList<>(ALTERNATIVE_COUNT);
        for (int i = 0; i < ALTERNATIVE_COUNT; i++) {
            if (i < alts.size()) {
                output.add(alts.get(i));
            } else {
                output.add(null);
            }
        }
        return Collections.unmodifiableList(output);
    }
}
