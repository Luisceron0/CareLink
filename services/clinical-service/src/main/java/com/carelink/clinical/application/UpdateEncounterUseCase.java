package com.carelink.clinical.application;

import com.carelink.clinical.domain.Encounter;
import com.carelink.clinical.domain.port.AuditLogPort;
import com.carelink.clinical.domain.port.EncounterRepository;
import com.carelink.clinical.domain.port.EncryptionPort;

import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.UUID;

/**
 * Caso de uso para actualizar encuentros no firmados.
 */
public final class UpdateEncounterUseCase {

    /** Repositorio de encuentros. */
    private final EncounterRepository encounterRepository;

    /** Puerto de auditoria. */
    private final AuditLogPort auditLogPort;

    /** Puerto de cifrado. */
    private final EncryptionPort encryptionPort;

    /**
     * Constructor.
     *
     * @param encounterRepositoryArg repositorio de encuentros
     * @param auditLogPortArg puerto de auditoria
     * @param encryptionPortArg puerto de cifrado
     */
    public UpdateEncounterUseCase(
            final EncounterRepository encounterRepositoryArg,
            final AuditLogPort auditLogPortArg,
            final EncryptionPort encryptionPortArg) {
        this.encounterRepository =
                Objects.requireNonNull(encounterRepositoryArg);
        this.auditLogPort = Objects.requireNonNull(auditLogPortArg);
        this.encryptionPort = Objects.requireNonNull(encryptionPortArg);
    }

    /**
     * Actualiza campos clinicos de un encuentro sin firma.
     *
     * @param tenantId tenant
     * @param actorUserId usuario actor
     * @param patientId paciente
     * @param encounterId encuentro
          * @param content contenido clinico
     * @return encuentro actualizado (descifrado)
     */
    public Encounter update(final UUID tenantId,
                            final UUID actorUserId,
                            final UUID patientId,
                            final UUID encounterId,
                            final EncounterContent content
    ) {
        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(actorUserId);
        Objects.requireNonNull(patientId);
        Objects.requireNonNull(encounterId);
                  Objects.requireNonNull(content);

        final Encounter encrypted = encounterRepository
                .findByTenantAndId(tenantId, encounterId)
                .orElseThrow(
                    () -> new NoSuchElementException("ENCOUNTER_NOT_FOUND")
                );

        if (!patientId.equals(encrypted.patientId())) {
            throw new SecurityException("CROSS_TENANT_ACCESS_DENIED");
        }

        final Encounter decrypted =
                encrypted.decryptClinicalContent(encryptionPort);
        final Encounter updated = decrypted.updateClinicalContent(
                content.chiefComplaint(),
                content.physicalExam(),
                content.treatmentPlan(),
                content.followUpInstructions()
        );

        final Encounter saved = encounterRepository
                .save(updated.encryptClinicalContent(encryptionPort));
        auditLogPort.recordPhiAccess(
                tenantId,
                actorUserId,
                patientId,
                "ENCOUNTER_UPDATED"
        );
        return saved.decryptClinicalContent(encryptionPort);
    }
}
