package com.carelink.clinical.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * FR-CLN-02. {@code signedAt == null} es un borrador — editable. {@code signedAt != null}
 * es inmutable, aplicado a nivel de base de datos por un trigger
 * (V4 de {@code tenant_template.sql}), no solo por lógica de aplicación: Ley 527/1999 y
 * Res. 1995/1999 lo exigen así, y "inmutable porque el código no lo deja editar" no
 * cumple eso — cualquiera con acceso directo a la base podría editarlo igual.
 *
 * <p><b>Alcance de esta sub-fase, diferido a propósito:</b> prescripciones estructuradas
 * (Sub-fase 6), enmiendas versionadas post-firma (FR-CLN-02 las menciona; el mecanismo
 * de "nueva entrada versionada que referencia la firmada" no se construye acá — con solo
 * bloquear la mutación ya se demuestra la garantía de inmutabilidad que AC-08 pide),
 * catálogo CIE-10 validado (se acepta el código como texto, sin validar contra la tabla
 * real de la OMS).
 */
public record ClinicalEncounter(
        UUID id,
        UUID patientId,
        UUID physicianUserId,
        String chiefComplaint,
        String examFindings,
        String diagnosisCie10,
        String treatmentPlan,
        String followUp,
        /** AC-06b — servicio del encounter, estampado del médico que lo abre. */
        String serviceId,
        OffsetDateTime createdAt,
        OffsetDateTime signedAt,
        UUID signedByUserId
) {
    public ClinicalEncounter {
        if (id == null) throw new IllegalArgumentException("ClinicalEncounter requiere id");
        if (patientId == null) throw new IllegalArgumentException("ClinicalEncounter requiere patientId");
        if (physicianUserId == null) throw new IllegalArgumentException("ClinicalEncounter requiere physicianUserId");
        if (chiefComplaint == null || chiefComplaint.isBlank()) {
            throw new IllegalArgumentException("ClinicalEncounter requiere chiefComplaint");
        }
        if (createdAt == null) throw new IllegalArgumentException("ClinicalEncounter requiere createdAt");
    }

    public boolean isSigned() {
        return signedAt != null;
    }
}
