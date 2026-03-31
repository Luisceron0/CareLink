package com.carelink.clinical.application;

import com.carelink.clinical.domain.Encounter;
import com.carelink.clinical.domain.port.AuditLogPort;
import com.carelink.clinical.domain.port.EncounterRepository;
import com.carelink.clinical.domain.port.EncryptionPort;

import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.UUID;

/**
 * Caso de uso para consultar encuentros clinicos.
 */
public final class GetEncounterUseCase {

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
    public GetEncounterUseCase(final EncounterRepository encounterRepositoryArg,
                               final AuditLogPort auditLogPortArg,
                               final EncryptionPort encryptionPortArg) {
        this.encounterRepository =
                Objects.requireNonNull(encounterRepositoryArg);
        this.auditLogPort = Objects.requireNonNull(auditLogPortArg);
        this.encryptionPort = Objects.requireNonNull(encryptionPortArg);
    }

    /**
     * Busca un encuentro por tenant, paciente e id.
     *
     * @param tenantId tenant
     * @param actorUserId usuario actor
     * @param patientId paciente
     * @param encounterId encuentro
     * @return encuentro descifrado
     */
    public Encounter get(final UUID tenantId,
                         final UUID actorUserId,
                         final UUID patientId,
                         final UUID encounterId) {
        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(actorUserId);
        Objects.requireNonNull(patientId);
        Objects.requireNonNull(encounterId);

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
        auditLogPort.recordPhiAccess(
                tenantId,
                actorUserId,
                patientId,
                "ENCOUNTER_READ"
        );
        return decrypted;
    }
}
