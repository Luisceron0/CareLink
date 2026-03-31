package com.carelink.clinical.domain.port;

import com.carelink.clinical.domain.Patient;

import java.util.UUID;

/**
 * Puerto para almacenar identidad desacoplada durante flujo GDPR.
 */
public interface GdprIdentityStore {

    /**
     * Guarda identidad y retorna token de seudonimizacion.
     *
     * @param tenantId tenant propietario
     * @param patientId paciente objetivo
     * @param patientIdentity datos de identidad en claro
     * @return token irreversible para referencia legal
     */
    String storeIdentityAndGenerateToken(
            UUID tenantId,
            UUID patientId,
            Patient patientIdentity
    );

    /**
     * Elimina identidad desacoplada por token.
     *
     * @param pseudonymToken token generado
     */
    void deleteIdentityByToken(String pseudonymToken);
}
