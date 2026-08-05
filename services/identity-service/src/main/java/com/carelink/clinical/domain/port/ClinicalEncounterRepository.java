package com.carelink.clinical.domain.port;

import com.carelink.clinical.domain.ClinicalEncounter;
import com.carelink.identity.domain.value.TenantSlug;

import java.util.Optional;
import java.util.UUID;

public interface ClinicalEncounterRepository {
    void save(TenantSlug tenantSlug, ClinicalEncounter encounter);

    Optional<ClinicalEncounter> findById(TenantSlug tenantSlug, UUID encounterId);

    /** Lanza {@code EncounterAlreadySignedException} si el encounter ya está firmado —
     * la base lo rechaza (trigger), este método traduce eso a una excepción de dominio. */
    void update(TenantSlug tenantSlug, ClinicalEncounter encounter);

    /** Lanza {@code EncounterAlreadySignedException} si ya estaba firmado. */
    void sign(TenantSlug tenantSlug, UUID encounterId, UUID signedByUserId);
}
