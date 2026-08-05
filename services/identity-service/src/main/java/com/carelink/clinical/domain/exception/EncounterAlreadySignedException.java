package com.carelink.clinical.domain.exception;

import java.util.UUID;

/** AC-08: PUT sobre un encounter firmado → 409. Lo lanza el adaptador de persistencia
 * cuando el trigger de la base rechaza la mutación, o cuando un intento de re-firmar
 * no afecta ninguna fila porque ya estaba firmado. */
public class EncounterAlreadySignedException extends RuntimeException {
    public EncounterAlreadySignedException(UUID encounterId) {
        super("ClinicalEncounter " + encounterId + " ya está firmado, es inmutable (FR-CLN-02)");
    }
}
