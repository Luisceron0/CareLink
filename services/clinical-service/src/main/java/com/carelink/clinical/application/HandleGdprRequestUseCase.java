package com.carelink.clinical.application;

import com.carelink.clinical.domain.Patient;
import com.carelink.clinical.domain.port.EncryptionPort;
import com.carelink.clinical.domain.port.GdprIdentityStore;
import com.carelink.clinical.domain.port.PatientRepository;
import com.carelink.clinical.domain.value.DocumentId;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Caso de uso para solicitudes GDPR frente a retencion clinica.
 */
public final class HandleGdprRequestUseCase {

    /** Codigo de jurisdiccion de la Union Europea. */
    private static final String JURISDICTION_EU = "EU";

    /** Codigo de jurisdiccion de Colombia. */
    private static final String JURISDICTION_CO = "CO";

    /** Tipo de solicitud de supresion. */
    private static final String REQUEST_ERASURE = "ERASURE";

    /** Marcador textual para identidad seudonimizada. */
    private static final String PSEUDONYMIZED_VALUE = "PSEUDONYMIZED";

    /** Tipo documental para token seudonimo. */
    private static final String GDPR_TOKEN_TYPE = "GDPR_TOKEN";

    /** Mensaje de retencion legal para pacientes CO. */
    private static final String CO_RETENTION_MESSAGE =
            "La historia clinica se conserva por obligacion legal en Colombia.";

    /** Base legal para retencion en Colombia. */
    private static final String CO_LEGAL_BASIS =
            "Retencion minima de 15 anios segun MinSalud (FR-CLN-06).";

    /** Resultado para solicitud retenida. */
    private static final String RESULT_RETAINED = "RETAINED";

    /** Resultado para solicitud seudonimizada. */
    private static final String RESULT_PSEUDONYMIZED = "PSEUDONYMIZED";

    /** Logger del flujo GDPR. */
    private static final Logger LOGGER =
            Logger.getLogger(HandleGdprRequestUseCase.class.getName());

    /** Repositorio de pacientes. */
    private final PatientRepository patientRepository;

    /** Store de identidad desacoplada GDPR. */
    private final GdprIdentityStore gdprIdentityStore;

    /** Puerto de cifrado PHI. */
    private final EncryptionPort encryptionPort;

    /**
     * Constructor.
     *
     * @param patientRepositoryArg repositorio de pacientes
     * @param gdprIdentityStoreArg store de identidad GDPR
     * @param encryptionPortArg cifrado PHI
     */
    public HandleGdprRequestUseCase(
            final PatientRepository patientRepositoryArg,
            final GdprIdentityStore gdprIdentityStoreArg,
            final EncryptionPort encryptionPortArg) {
        this.patientRepository = Objects.requireNonNull(patientRepositoryArg);
        this.gdprIdentityStore = Objects.requireNonNull(gdprIdentityStoreArg);
        this.encryptionPort = Objects.requireNonNull(encryptionPortArg);
    }

    /**
     * Procesa solicitud GDPR segun jurisdiccion.
     *
     * @param tenantId tenant
     * @param actorUserId usuario solicitante
     * @param patientId paciente
     * @param requestType tipo de solicitud
     * @param jurisdiction jurisdiccion legal
     * @param confirmed confirmacion explicita para borrado EU
     * @return decision aplicada
     */
    public GdprRequestDecision handle(
            final UUID tenantId,
            final UUID actorUserId,
            final UUID patientId,
            final String requestType,
            final String jurisdiction,
            final boolean confirmed) {
        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(actorUserId);
        Objects.requireNonNull(patientId);
        Objects.requireNonNull(requestType);
        Objects.requireNonNull(jurisdiction);

        final Patient encrypted = patientRepository.findByTenantAndId(
                tenantId,
                patientId
        ).orElseThrow(() -> new NoSuchElementException("PATIENT_NOT_FOUND"));

        final String normalizedJurisdiction = jurisdiction.trim().toUpperCase();
        final String normalizedType = requestType.trim().toUpperCase();

        if (JURISDICTION_CO.equals(normalizedJurisdiction)) {
            final GdprRequestDecision decision = new GdprRequestDecision(
                    RESULT_RETAINED,
                    CO_RETENTION_MESSAGE,
                    CO_LEGAL_BASIS,
                    null,
                    Instant.now()
            );
            logDecision(
                    tenantId,
                    actorUserId,
                    normalizedType,
                    normalizedJurisdiction,
                    decision
            );
            return decision;
        }

        if (!JURISDICTION_EU.equals(normalizedJurisdiction)) {
            throw new IllegalArgumentException("UNSUPPORTED_JURISDICTION");
        }

        if (!REQUEST_ERASURE.equals(normalizedType)) {
            throw new IllegalArgumentException("UNSUPPORTED_GDPR_REQUEST_TYPE");
        }

        if (!confirmed) {
            throw new IllegalArgumentException("GDPR_CONFIRMATION_REQUIRED");
        }

        final Patient decrypted = encrypted.decryptPhi(encryptionPort);
        final String pseudonymToken =
                gdprIdentityStore.storeIdentityAndGenerateToken(
                tenantId,
                patientId,
                decrypted
        );

        final Patient pseudonymized = toPseudonymizedPatient(
                decrypted,
                pseudonymToken
        ).encryptPhi(encryptionPort);
        patientRepository.save(pseudonymized);
        gdprIdentityStore.deleteIdentityByToken(pseudonymToken);

        final GdprRequestDecision decision = new GdprRequestDecision(
                RESULT_PSEUDONYMIZED,
                "Identidad eliminada; historia clinica retenida seudonimizada.",
                "GDPR Art.17 con retencion legal FR-CLN-06.",
                pseudonymToken,
                Instant.now()
        );
        logDecision(
                tenantId,
                actorUserId,
                normalizedType,
                normalizedJurisdiction,
                decision
        );
        return decision;
    }

    private Patient toPseudonymizedPatient(
            final Patient patient,
            final String pseudonymToken) {
        return new Patient(
                patient.id(),
                patient.tenantId(),
                PSEUDONYMIZED_VALUE,
                new DocumentId(GDPR_TOKEN_TYPE, pseudonymToken),
                patient.bloodType(),
                PSEUDONYMIZED_VALUE,
                PSEUDONYMIZED_VALUE,
                PSEUDONYMIZED_VALUE,
                patient.allergies(),
                patient.activeMedications(),
                patient.createdAt()
        );
    }

    private void logDecision(
            final UUID tenantId,
            final UUID actorUserId,
            final String requestType,
            final String jurisdiction,
            final GdprRequestDecision decision) {
        LOGGER.info(
                "gdpr_request"
                        + " request_type=" + requestType
                        + " jurisdiction=" + jurisdiction
                        + " result=" + decision.result()
                        + " base_legal=" + sanitize(decision.baseLegalApplied())
                        + " timestamp=" + decision.processedAt()
                        + " tenant_id_hashed=" + hashIdentifier(tenantId)
                        + " user_id_hashed=" + hashIdentifier(actorUserId)
        );
    }

    private String sanitize(final String text) {
        return text.replace(' ', '_');
    }

    private String hashIdentifier(final UUID value) {
        try {
            final MessageDigest digest = MessageDigest.getInstance("SHA-256");
            final byte[] bytes = digest.digest(
                    value.toString().getBytes(StandardCharsets.UTF_8)
            );
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException exception) {
                        throw new IllegalStateException(
                                        "HASH_ALGORITHM_UNAVAILABLE",
                                        exception
                        );
        }
    }

    /**
     * Resultado del procesamiento GDPR.
     *
     * @param result estado de procesamiento
     * @param businessMessage mensaje de negocio
     * @param baseLegalApplied base legal aplicada
     * @param pseudonymToken token seudonimo si aplica
     * @param processedAt fecha de procesamiento
     */
    public record GdprRequestDecision(
            String result,
            String businessMessage,
            String baseLegalApplied,
            String pseudonymToken,
            Instant processedAt) {
    }
}
