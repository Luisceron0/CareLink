package com.carelink.clinical.domain;

import com.carelink.clinical.domain.exception.ImmutableRecordException;
import com.carelink.clinical.domain.port.EncryptionPort;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Encuentro clínico.
 *
 * @param id identificador del encuentro
 * @param tenantId tenant propietario
 * @param patientId paciente asociado
 * @param physicianId médico responsable
 * @param chiefComplaint motivo de consulta
 * @param physicalExam examen físico
 * @param treatmentPlan plan terapéutico
 * @param followUpInstructions instrucciones de seguimiento
 * @param prescriptions prescripciones del encuentro
 * @param signedAt marca de firma clínica (inmutable si no es null)
 * @param createdAt fecha de creación
 */
public record Encounter(UUID id,
                        UUID tenantId,
                        UUID patientId,
                        UUID physicianId,
                        String chiefComplaint,
                        String physicalExam,
                        String treatmentPlan,
                        String followUpInstructions,
                        List<Prescription> prescriptions,
                        Instant signedAt,
                        Instant createdAt) {

    /**
     * Constructor canónico.
     */
    public Encounter {
        Objects.requireNonNull(id);
        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(patientId);
        Objects.requireNonNull(physicianId);
        Objects.requireNonNull(chiefComplaint);
        Objects.requireNonNull(physicalExam);
        Objects.requireNonNull(treatmentPlan);
        Objects.requireNonNull(followUpInstructions);
        Objects.requireNonNull(prescriptions);
        Objects.requireNonNull(createdAt);
    }

    /**
     * Devuelve encuentro firmado, bloqueando cambios posteriores.
     *
     * @param signedTimestamp marca de firma
     * @return encuentro firmado
     */
    public Encounter sign(final Instant signedTimestamp) {
        Objects.requireNonNull(signedTimestamp);
        if (signedAt != null) {
            throw new ImmutableRecordException("ENCOUNTER_ALREADY_SIGNED");
        }
        return new Encounter(
                id,
                tenantId,
                patientId,
                physicianId,
                chiefComplaint,
                physicalExam,
                treatmentPlan,
                followUpInstructions,
                prescriptions,
                signedTimestamp,
                createdAt
        );
    }

    /**
     * Actualiza contenido clínico solo si no está firmado.
     *
     * @param complaint nuevo motivo de consulta
     * @param exam nuevo examen físico
     * @param plan nuevo plan
     * @param followUp nuevas instrucciones
     * @return nueva instancia de encuentro
     */
    public Encounter updateClinicalContent(final String complaint,
                                           final String exam,
                                           final String plan,
                                           final String followUp) {
        if (signedAt != null) {
            throw new ImmutableRecordException("ENCOUNTER_ALREADY_SIGNED");
        }
        return new Encounter(
                id,
                tenantId,
                patientId,
                physicianId,
                Objects.requireNonNull(complaint),
                Objects.requireNonNull(exam),
                Objects.requireNonNull(plan),
                Objects.requireNonNull(followUp),
                prescriptions,
                signedAt,
                createdAt
        );
    }

    /**
     * Cifra contenido clínico PHI para persistencia.
     *
     * @param encryptionPort puerto de cifrado
     * @return encuentro con contenido clínico cifrado
     */
        public Encounter encryptClinicalContent(
            final EncryptionPort encryptionPort) {
        Objects.requireNonNull(encryptionPort);
        return new Encounter(
                id,
                tenantId,
                patientId,
                physicianId,
                encryptionPort.encrypt(tenantId, chiefComplaint),
                encryptionPort.encrypt(tenantId, physicalExam),
                encryptionPort.encrypt(tenantId, treatmentPlan),
                encryptionPort.encrypt(tenantId, followUpInstructions),
                prescriptions,
                signedAt,
                createdAt
        );
    }

    /**
     * Descifra contenido clínico PHI para respuesta de negocio.
     *
     * @param encryptionPort puerto de cifrado
     * @return encuentro con contenido clínico descifrado
     */
        public Encounter decryptClinicalContent(
            final EncryptionPort encryptionPort) {
        Objects.requireNonNull(encryptionPort);
        return new Encounter(
                id,
                tenantId,
                patientId,
                physicianId,
                encryptionPort.decrypt(tenantId, chiefComplaint),
                encryptionPort.decrypt(tenantId, physicalExam),
                encryptionPort.decrypt(tenantId, treatmentPlan),
                encryptionPort.decrypt(tenantId, followUpInstructions),
                prescriptions,
                signedAt,
                createdAt
        );
    }
}
