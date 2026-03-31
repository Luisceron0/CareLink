package com.carelink.scheduling.infrastructure.web;

import com.carelink.scheduling.domain.exception.SlotAlreadyBookedException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;

/**
 * Manejador de excepciones para endpoints de appointments.
 */
@RestControllerAdvice
public final class AppointmentExceptionHandler {

    /**
     * Maneja colisiones de slot de appointment.
     *
     * @param ex excepción de colisión de slot
     * @return response HTTP 409 con alternativas de reserva
     */
    @ExceptionHandler(SlotAlreadyBookedException.class)
    public ResponseEntity<ApiErrorResponse> handleSlotConflict(
            final SlotAlreadyBookedException ex
    ) {
        final ApiErrorResponse body = new ApiErrorResponse(
                "SLOT_ALREADY_BOOKED",
                "Requested slot is not available",
                ex.alternatives(),
                LocalDateTime.now()
        );
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }
}
