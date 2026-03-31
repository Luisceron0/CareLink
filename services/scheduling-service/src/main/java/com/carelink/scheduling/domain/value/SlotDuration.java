package com.carelink.scheduling.domain.value;

/**
 * Duración de slot en minutos.
 *
 * @param minutes minutos del slot
 */
public record SlotDuration(int minutes) {

    /**
     * Valida la duración en la construcción.
     *
     * @param minutes minutos del slot
     */
    public SlotDuration {
        if (minutes <= 0) {
            throw new IllegalArgumentException("Slot duration must be > 0");
        }
    }
}
