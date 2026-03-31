package com.carelink.clinical.application;

import com.carelink.clinical.domain.Encounter;
import com.carelink.clinical.domain.event.EncounterSigned;
import com.carelink.clinical.domain.port.AuditLogPort;
import com.carelink.clinical.domain.port.EncounterRepository;
import com.carelink.clinical.domain.port.EncryptionPort;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Caso de uso para firmar encuentros clínicos.
 */
public final class SignEncounterUseCase {

    /** Repositorio de encuentros. */
    private final EncounterRepository encounterRepository;

    /** Puerto de auditoría. */
    private final AuditLogPort auditLogPort;

    /** Puerto de cifrado. */
    private final EncryptionPort encryptionPort;

    /** Cifrado no-op para compatibilidad de pruebas unitarias. */
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
     * @param encounterRepositoryArg repositorio de encuentros
     * @param auditLogPortArg puerto de auditoría
     */
    public SignEncounterUseCase(
            final EncounterRepository encounterRepositoryArg,
            final AuditLogPort auditLogPortArg) {
        this(encounterRepositoryArg, auditLogPortArg, NO_OP_ENCRYPTION);
        }

        /**
         * Constructor completo.
         *
         * @param encounterRepositoryArg repositorio de encuentros
         * @param auditLogPortArg puerto de auditoría
         * @param encryptionPortArg puerto de cifrado
         */
        public SignEncounterUseCase(
            final EncounterRepository encounterRepositoryArg,
            final AuditLogPort auditLogPortArg,
            final EncryptionPort encryptionPortArg) {
        this.encounterRepository =
                Objects.requireNonNull(encounterRepositoryArg);
        this.auditLogPort = Objects.requireNonNull(auditLogPortArg);
        this.encryptionPort = Objects.requireNonNull(encryptionPortArg);
    }

    /**
     * Firma encuentro y emite evento.
     *
     * @param tenantId tenant
     * @param encounterId encuentro
     * @param physicianId médico firmante
     * @return evento EncounterSigned
     */
    public EncounterSigned sign(final UUID tenantId,
                                final UUID encounterId,
                                final UUID physicianId) {
        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(encounterId);
        Objects.requireNonNull(physicianId);

        final Encounter current = encounterRepository
            .findByTenantAndId(tenantId, encounterId)
            .orElseThrow(
                () -> new IllegalArgumentException("Encounter not found")
            );
        final Encounter decrypted =
            current.decryptClinicalContent(encryptionPort);
        final Encounter signed = decrypted.sign(Instant.now());
        final Encounter encrypted =
            signed.encryptClinicalContent(encryptionPort);
        final Encounter saved = encounterRepository.save(encrypted);

        auditLogPort.recordPhiAccess(
            tenantId,
            physicianId,
            saved.patientId(),
            "ENCOUNTER_SIGNED"
        );
        return new EncounterSigned(
            tenantId,
            saved.id(),
            physicianId,
            Instant.now()
        );
    }
}
