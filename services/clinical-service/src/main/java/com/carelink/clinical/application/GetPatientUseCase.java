package com.carelink.clinical.application;

import com.carelink.clinical.domain.Patient;
import com.carelink.clinical.domain.port.AuditLogPort;
import com.carelink.clinical.domain.port.EncryptionPort;
import com.carelink.clinical.domain.port.PatientRepository;

import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.UUID;

/**
 * Caso de uso para lectura de paciente con auditoría de PHI.
 */
public final class GetPatientUseCase {

    /** Repositorio de pacientes. */
    private final PatientRepository patientRepository;

    /** Puerto de auditoría. */
    private final AuditLogPort auditLogPort;

    /** Puerto de cifrado. */
    private final EncryptionPort encryptionPort;

    /**
     * Constructor.
     *
     * @param patientRepositoryArg repositorio
     * @param auditLogPortArg auditoría
     * @param encryptionPortArg cifrado
     */
    public GetPatientUseCase(final PatientRepository patientRepositoryArg,
                             final AuditLogPort auditLogPortArg,
                             final EncryptionPort encryptionPortArg) {
        this.patientRepository = Objects.requireNonNull(patientRepositoryArg);
        this.auditLogPort = Objects.requireNonNull(auditLogPortArg);
        this.encryptionPort = Objects.requireNonNull(encryptionPortArg);
    }

    /**
     * Obtiene paciente, descifra PHI y registra auditoría de lectura.
     *
     * @param tenantId tenant
     * @param actorUserId actor
     * @param patientId paciente
     * @return paciente descifrado
     */
    public Patient getById(final UUID tenantId,
                           final UUID actorUserId,
                           final UUID patientId) {
        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(actorUserId);
        Objects.requireNonNull(patientId);

        final Patient encrypted = patientRepository
                .findByTenantAndId(tenantId, patientId)
                .orElseThrow(
                    () -> new NoSuchElementException("PATIENT_NOT_FOUND")
                );

        final Patient decrypted = encrypted.decryptPhi(encryptionPort);
        auditLogPort.recordPhiAccess(
                tenantId,
                actorUserId,
                patientId,
                "PATIENT_READ"
        );
        return decrypted;
    }
}
