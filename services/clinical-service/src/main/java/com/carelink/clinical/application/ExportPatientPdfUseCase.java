package com.carelink.clinical.application;

import com.carelink.clinical.domain.Patient;
import com.carelink.clinical.domain.port.AuditLogPort;
import com.carelink.clinical.domain.port.EncryptionPort;
import com.carelink.clinical.domain.port.PatientRepository;

import java.nio.charset.StandardCharsets;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.UUID;

/**
 * Caso de uso para exportar paciente en un PDF simplificado.
 */
public final class ExportPatientPdfUseCase {

    /** Repositorio de pacientes. */
    private final PatientRepository patientRepository;

    /** Puerto de auditoria. */
    private final AuditLogPort auditLogPort;

    /** Puerto de cifrado. */
    private final EncryptionPort encryptionPort;

    /**
     * Constructor.
     *
     * @param patientRepositoryArg repositorio
     * @param auditLogPortArg auditoria
     * @param encryptionPortArg cifrado
     */
    public ExportPatientPdfUseCase(final PatientRepository patientRepositoryArg,
                                   final AuditLogPort auditLogPortArg,
                                   final EncryptionPort encryptionPortArg) {
        this.patientRepository = Objects.requireNonNull(patientRepositoryArg);
        this.auditLogPort = Objects.requireNonNull(auditLogPortArg);
        this.encryptionPort = Objects.requireNonNull(encryptionPortArg);
    }

    /**
     * Exporta un resumen PDF minimamente viable.
     *
     * @param tenantId tenant
     * @param actorUserId actor
     * @param patientId paciente
     * @return bytes del documento
     */
    public byte[] export(final UUID tenantId,
                         final UUID actorUserId,
                         final UUID patientId) {
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
                "PATIENT_EXPORT_PDF"
        );

        final String content = "Patient Clinical Summary\n"
                + "id=" + decrypted.id() + "\n"
                + "name=" + decrypted.fullName() + "\n"
                + "document=" + decrypted.documentId().type() + " "
                + decrypted.documentId().value() + "\n"
                + "bloodType=" + decrypted.bloodType().name() + "\n";
        return content.getBytes(StandardCharsets.UTF_8);
    }
}
