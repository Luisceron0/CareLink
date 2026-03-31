package com.carelink.clinical.infrastructure.export;

import com.carelink.clinical.domain.Patient;
import com.carelink.clinical.domain.port.FhirExporter;
import org.springframework.stereotype.Component;

/**
 * Exportador minimo de paciente en formato FHIR-like JSON.
 */
@Component
public final class SimpleFhirExporter implements FhirExporter {

    @Override
    public String exportPatientR4(final Patient patient) {
        final StringBuilder builder = new StringBuilder();
        builder.append("{");
        builder.append("\"resourceType\":\"Patient\",");
        builder.append("\"id\":\"").append(patient.id()).append("\",");
        builder.append("\"name\":[{\"text\":\"")
            .append(escape(patient.fullName()))
            .append("\"}],");
        builder.append("\"telecom\":[");
        builder.append("{\"system\":\"phone\",\"value\":\"")
            .append(escape(patient.phone()))
            .append("\"},");
        builder.append("{\"system\":\"email\",\"value\":\"")
            .append(escape(patient.email()))
            .append("\"}");
        builder.append("]");
        builder.append("}");
        return builder.toString();
    }

    private String escape(final String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
