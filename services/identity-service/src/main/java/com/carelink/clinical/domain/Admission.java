package com.carelink.clinical.domain;

import com.carelink.clinical.domain.value.AdmissionType;
import com.carelink.clinical.domain.value.TriagePriority;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * FR-CLN-03. {@code triagePriority} es obligatorio para {@link AdmissionType#URGENCIAS}
 * y nulo para {@link AdmissionType#CONSULTA_EXTERNA} — Triage Manchester es
 * específicamente una herramienta de urgencias, no algo que consulta externa clasifique
 * de la misma forma. Esta regla vive en {@code RegisterAdmissionUseCase}, no acá: el
 * record en sí no impone la relación entre sus propios campos (mismo criterio que
 * {@code ClinicalEncounter}, donde el dominio ve datos ya válidos).
 *
 * <p>{@code clinicalEncounterId} es nulo hasta que se abre un encounter para esta
 * admisión — {@code LinkEncounterToAdmissionUseCase} lo completa.
 */
public record Admission(UUID id, UUID patientId, AdmissionType admissionType, TriagePriority triagePriority,
                         UUID admittedByUserId, OffsetDateTime admittedAt, UUID clinicalEncounterId,
                         OffsetDateTime createdAt) {}
