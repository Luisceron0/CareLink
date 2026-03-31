package com.carelink.clinical.infrastructure.web;

import com.carelink.clinical.domain.exception.ImmutableRecordException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * Mapeo centralizado de errores para clinical-service.
 */
@RestControllerAdvice
public final class ClinicalExceptionHandler {

    /**
     * Mapea error por encounter firmado e inmutable.
     *
     * @param exception excepcion de inmutabilidad
     * @return respuesta sanitizada con HTTP 409
     */
    @ExceptionHandler(ImmutableRecordException.class)
    public ResponseEntity<ClinicalDtos.ApiError> handleImmutable(
            final ImmutableRecordException exception) {
        return buildError(
                HttpStatus.CONFLICT,
                "ENCOUNTER_ALREADY_SIGNED",
                "Unable to update signed encounter"
        );
    }

    /**
     * Mapea intentos de acceso no autorizado.
     *
     * @param exception excepcion de seguridad
     * @return respuesta sanitizada con HTTP 403
     */
    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<ClinicalDtos.ApiError> handleForbidden(
            final SecurityException exception) {
        return buildError(HttpStatus.FORBIDDEN, "FORBIDDEN", "Access denied");
    }

    /**
     * Mapea recursos inexistentes.
     *
     * @param exception excepcion de no encontrado
     * @return respuesta sanitizada con HTTP 404
     */
    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<ClinicalDtos.ApiError> handleNotFound(
            final NoSuchElementException exception) {
        return buildError(
                HttpStatus.NOT_FOUND,
                "RESOURCE_NOT_FOUND",
                "Requested resource does not exist"
        );
    }

    /**
     * Mapea errores de validacion de entrada.
     *
     * @param exception excepcion de argumento invalido
     * @return respuesta sanitizada con HTTP 400
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ClinicalDtos.ApiError> handleBadRequest(
            final IllegalArgumentException exception) {
        return buildError(
            HttpStatus.BAD_REQUEST,
            "INVALID_INPUT",
            "Invalid input"
        );
    }

    /**
     * Mapea errores no controlados del servicio.
     *
     * @param exception excepcion interna
     * @return respuesta sanitizada con HTTP 500
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ClinicalDtos.ApiError> handleGeneric(
            final Exception exception) {
        return buildError(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "INTERNAL_ERROR",
                "Unable to complete request"
        );
    }

    private ResponseEntity<ClinicalDtos.ApiError> buildError(
            final HttpStatus status,
            final String code,
            final String message) {
        final String requestId = UUID.randomUUID().toString();
        return ResponseEntity.status(status)
                .body(new ClinicalDtos.ApiError(code, message, requestId));
    }
}
