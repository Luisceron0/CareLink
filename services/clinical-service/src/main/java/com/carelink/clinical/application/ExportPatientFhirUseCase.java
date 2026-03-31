package com.carelink.clinical.application;

import com.carelink.clinical.domain.Patient;
import com.carelink.clinical.domain.port.AuditLogPort;
import com.carelink.clinical.domain.port.EncryptionPort;
import com.carelink.clinical.domain.port.FhirExporter;
import com.carelink.clinical.domain.port.PatientRepository;

import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.UUID;

/**
 * Caso de uso para exportar paciente en formato FHIR.
 */
public final class ExportPatientFhirUseCase {

    /** Repositorio de pacientes. */
    private final PatientRepository patientRepository;

    /** Puerto de exportacion FHIR. */
    private final FhirExporter fhirExporter;

    /** Puerto de auditoria. */
    private final AuditLogPort auditLogPort;

    /** Puerto de cifrado. */
    private final EncryptionPort encryptionPort;

    /**
     * Constructor.
     *
     * @param patientRepositoryArg repositorio
     * @param fhirExporterArg exportador
     * @param auditLogPortArg auditoria
     * @param encryptionPortArg cifrado
     */
        public ExportPatientFhirUseCase(
            final PatientRepository patientRepositoryArg,
            final FhirExporter fhirExporterArg,
            final AuditLogPort auditLogPortArg,
            final EncryptionPort encryptionPortArg) {
        this.patientRepository = Objects.requireNonNull(patientRepositoryArg);
        this.fhirExporter = Objects.requireNonNull(fhirExporterArg);
        this.auditLogPort = Objects.requireNonNull(auditLogPortArg);
        this.encryptionPort = Objects.requireNonNull(encryptionPortArg);
    }

    /**
     * Exporta paciente a FHIR y registra auditoria.
     *
     * @param tenantId tenant
     * @param actorUserId actor
     * @param patientId paciente
     * @return json FHIR
     */
    public String export(final UUID tenantId,
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
                "PATIENT_EXPORT_FHIR"
        );
        return fhirExporter.exportPatientR4(decrypted);
    }
}
