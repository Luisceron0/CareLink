package com.carelink.scheduling.infrastructure.web;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Response estándar para errores de API.
 *
 * @param code         código de error interno
 * @param message      mensaje de error
 * @param alternatives alternativas de slot, cuando aplique
 * @param timestamp    marca temporal del error
 */
public record ApiErrorResponse(
        String code,
        String message,
        List<LocalDateTime> alternatives,
        LocalDateTime timestamp
) {
}
