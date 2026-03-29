package com.carelink.scheduling.domain;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Representa un slot de tiempo para un physician.
 *
 * @param id          identificador del slot
 * @param physicianId identificador del physician dueño del slot
 * @param start       fecha y hora de inicio
 * @param duration    duración del slot
 * @param status      estado del slot
 */
public record TimeSlot(
        UUID id,
        UUID physicianId,
        LocalDateTime start,
        Duration duration,
        String status) {
}
