package com.carelink.clinical.domain;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * FR-CLN-04. Todos los valores son opcionales: una toma de signos vitales real rara vez
 * incluye los seis, y exigirlos obligaría a inventar datos para poder guardar los que sí
 * se midieron — peor que un nulo honesto en una historia clínica.
 *
 * <p>Sin cifrar en la base, a diferencia de las notas de texto libre: una medición no
 * identifica a nadie por sí sola (mismo criterio que {@code blood_type} en Patient), y
 * dejarlas numéricas permite evaluarlas en SQL sin descifrar cada fila.
 */
public record VitalSigns(
        UUID id,
        UUID diaryEntryId,
        Integer systolicMmHg,
        Integer diastolicMmHg,
        Integer heartRateBpm,
        Integer respiratoryRate,
        BigDecimal temperatureCelsius,
        Integer oxygenSaturation,
        OffsetDateTime recordedAt
) {}
