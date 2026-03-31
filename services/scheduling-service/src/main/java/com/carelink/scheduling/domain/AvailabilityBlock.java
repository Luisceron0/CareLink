package com.carelink.scheduling.domain;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.UUID;

/**
 * Bloque de disponibilidad recurrente por día de la semana.
 *
 * @param id          identificador del bloque
 * @param physicianId identificador del physician
 * @param dayOfWeek   día de la semana
 * @param startTime   hora de inicio
 * @param endTime     hora de fin
 */
public record AvailabilityBlock(
        UUID id,
        UUID physicianId,
        DayOfWeek dayOfWeek,
        LocalTime startTime,
        LocalTime endTime) {
}
