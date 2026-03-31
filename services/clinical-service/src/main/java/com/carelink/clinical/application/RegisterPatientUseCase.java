package com.carelink.clinical.application;

import com.carelink.clinical.domain.Patient;
import com.carelink.clinical.domain.event.PatientRegistered;
import com.carelink.clinical.domain.port.AuditLogPort;
import com.carelink.clinical.domain.port.EncryptionPort;
import com.carelink.clinical.domain.port.PatientRepository;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Caso de uso para registrar pacientes.
 */
public final class RegisterPatientUseCase {

    /** Repositorio de pacientes. */
    private final PatientRepository patientRepository;

    /** Puerto de auditoría. */
    private final AuditLogPort auditLogPort;

    /** Puerto de cifrado. */
    private final EncryptionPort encryptionPort;

    /** Cifrado no-op para compatibilidad en pruebas unitarias. */
    private static final EncryptionPort NO_OP_ENCRYPTION =
            new EncryptionPort() {
                @Override
                public String encrypt(final UUID tenantId,
                                      final String plaintext) {
                    return plaintext;
                }

                @Override
                public String decrypt(final UUID tenantId,
                                      final String ciphertext) {
                    return ciphertext;
                }
            };

    /**
     * Constructor.
     *
     * @param patientRepositoryArg repositorio de pacientes
     * @param auditLogPortArg puerto de auditoría
     */
    public RegisterPatientUseCase(final PatientRepository patientRepositoryArg,
                                  final AuditLogPort auditLogPortArg) {
        this(patientRepositoryArg, auditLogPortArg, NO_OP_ENCRYPTION);
    }

    /**
     * Constructor completo.
     *
     * @param patientRepositoryArg repositorio de pacientes
     * @param auditLogPortArg puerto de auditoría
     * @param encryptionPortArg puerto de cifrado
     */
    public RegisterPatientUseCase(final PatientRepository patientRepositoryArg,
                                  final AuditLogPort auditLogPortArg,
                                  final EncryptionPort encryptionPortArg) {
        this.patientRepository = Objects.requireNonNull(patientRepositoryArg);
        this.auditLogPort = Objects.requireNonNull(auditLogPortArg);
        this.encryptionPort = Objects.requireNonNull(encryptionPortArg);
    }

    /**
     * Registra paciente y retorna evento de dominio.
     *
     * @param actorUserId usuario actor
     * @param patient paciente a registrar
     * @return evento PatientRegistered
     */
    public PatientRegistered register(final UUID actorUserId,
                                      final Patient patient) {
        Objects.requireNonNull(actorUserId);
        Objects.requireNonNull(patient);

        final Patient encrypted = patient.encryptPhi(encryptionPort);
        final Patient saved = patientRepository.save(encrypted);
        auditLogPort.recordPhiAccess(
                saved.tenantId(),
                actorUserId,
                saved.id(),
                "PATIENT_REGISTERED"
        );
        return new PatientRegistered(
            saved.tenantId(),
            saved.id(),
            Instant.now()
        );
    }
}
