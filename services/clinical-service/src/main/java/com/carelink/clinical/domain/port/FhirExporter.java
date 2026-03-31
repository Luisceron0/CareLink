package com.carelink.clinical.domain.port;

import com.carelink.clinical.domain.Patient;

/**
 * Puerto para exportación FHIR.
 */
public interface FhirExporter {

    /**
     * Exporta paciente a FHIR R4 JSON.
     *
     * @param patient paciente
     * @return contenido FHIR serializado
     */
    String exportPatientR4(Patient patient);
}
