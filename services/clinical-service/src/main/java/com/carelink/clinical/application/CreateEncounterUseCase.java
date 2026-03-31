package com.carelink.clinical.application;

import com.carelink.clinical.domain.Encounter;
import com.carelink.clinical.domain.port.AuditLogPort;
import com.carelink.clinical.domain.port.EncounterRepository;
import com.carelink.clinical.domain.port.EncryptionPort;
import com.carelink.clinical.domain.port.PatientRepository;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.UUID;

/**
 * Caso de uso para crear encuentros clinicos.
 */
public final class CreateEncounterUseCase {

    /** Repositorio de encuentros. */
    private final EncounterRepository encounterRepository;

    /** Repositorio de pacientes. */
    private final PatientRepository patientRepository;

    /** Puerto de auditoria. */
    private final AuditLogPort auditLogPort;

    /** Puerto de cifrado. */
    private final EncryptionPort encryptionPort;

    /**
     * Constructor.
     *
     * @param encounterRepositoryArg repositorio de encuentros
     * @param patientRepositoryArg repositorio de pacientes
     * @param auditLogPortArg puerto de auditoria
     * @param encryptionPortArg puerto de cifrado
     */
    public CreateEncounterUseCase(
            final EncounterRepository encounterRepositoryArg,
            final PatientRepository patientRepositoryArg,
            final AuditLogPort auditLogPortArg,
            final EncryptionPort encryptionPortArg) {
        this.encounterRepository =
                Objects.requireNonNull(encounterRepositoryArg);
        this.patientRepository = Objects.requireNonNull(patientRepositoryArg);
        this.auditLogPort = Objects.requireNonNull(auditLogPortArg);
        this.encryptionPort = Objects.requireNonNull(encryptionPortArg);
    }

    /**
     * Crea un encuentro para un paciente del tenant.
     *
     * @param tenantId tenant
     * @param actorUserId usuario actor
     * @param patientId paciente
     * @param physicianId medico responsable
          * @param content contenido clinico
     * @return encuentro persistido (descifrado)
     */
    public Encounter create(final UUID tenantId,
                            final UUID actorUserId,
                            final UUID patientId,
                            final UUID physicianId,
                            final EncounterContent content
    ) {
        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(actorUserId);
        Objects.requireNonNull(patientId);
        Objects.requireNonNull(physicianId);
                  Objects.requireNonNull(content);

        patientRepository.findByTenantAndId(tenantId, patientId)
                                .orElseThrow(
                                        () -> new NoSuchElementException(
                                                        "PATIENT_NOT_FOUND"
                                        )
                                );

        final Encounter encounter = new Encounter(
                UUID.randomUUID(),
                tenantId,
                patientId,
                physicianId,
                content.chiefComplaint(),
                content.physicalExam(),
                content.treatmentPlan(),
                content.followUpInstructions(),
                List.of(),
                null,
                Instant.now()
        );
        final Encounter encrypted =
                encounter.encryptClinicalContent(encryptionPort);
        final Encounter saved = encounterRepository.save(encrypted);

        auditLogPort.recordPhiAccess(
                tenantId,
                actorUserId,
                patientId,
                "ENCOUNTER_CREATED"
        );
        return saved.decryptClinicalContent(encryptionPort);
    }
}
