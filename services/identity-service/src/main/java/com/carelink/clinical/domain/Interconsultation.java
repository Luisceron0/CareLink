package com.carelink.clinical.domain;

import com.carelink.clinical.domain.value.InterconsultationStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * FR-CLN-08, FR-CLN-10.
 *
 * <p>El acceso del especialista al paciente NO se guarda en ningún lado: se deriva de
 * {@code status == OPEN} en cada request. Esa es la diferencia entre este diseño y una
 * tabla de permisos concedidos — un permiso persistido es un estado que hay que
 * acordarse de revocar, y "olvidarse de revocar" es la clase de bug que FR-CLN-10
 * existe para hacer imposible, no para detectar.
 */
public record Interconsultation(
        UUID id,
        UUID patientId,
        UUID clinicalEncounterId,
        UUID requestingPhysicianId,
        UUID specialistUserId,
        String question,
        InterconsultationStatus status,
        OffsetDateTime requestedAt,
        OffsetDateTime closedAt,
        InterconsultationResponse response,
        String serviceId
) {
    public Interconsultation {
        if (patientId == null) throw new IllegalArgumentException("Interconsultation requiere patientId");
        if (clinicalEncounterId == null) {
            throw new IllegalArgumentException("Interconsultation requiere clinicalEncounterId (trazabilidad, FR-CLN-09)");
        }
        if (specialistUserId == null) throw new IllegalArgumentException("Interconsultation requiere specialistUserId");
        if (question == null || question.isBlank()) {
            throw new IllegalArgumentException("Interconsultation requiere una pregunta");
        }
    }

    public boolean isOpen() {
        return status == InterconsultationStatus.OPEN;
    }
}
