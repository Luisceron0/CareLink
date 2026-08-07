package com.carelink.clinical.domain.port;

import com.carelink.clinical.domain.ClinicalEncounter;
import com.carelink.clinical.domain.value.ServiceScope;
import com.carelink.identity.domain.value.TenantSlug;

import java.util.Optional;
import java.util.UUID;

public interface ClinicalEncounterRepository {
    void save(TenantSlug tenantSlug, ClinicalEncounter encounter);

    /** AC-06b: {@code scope} filtra por servicio; un encounter de otro servicio se ve igual que uno inexistente. */
    Optional<ClinicalEncounter> findById(TenantSlug tenantSlug, UUID encounterId, ServiceScope scope);

    /** Lanza {@code EncounterAlreadySignedException} si el encounter ya está firmado —
     * la base lo rechaza (trigger), este método traduce eso a una excepción de dominio. */
    void update(TenantSlug tenantSlug, ClinicalEncounter encounter);

    /** Lanza {@code EncounterAlreadySignedException} si ya estaba firmado. */
    void sign(TenantSlug tenantSlug, UUID encounterId, UUID signedByUserId);
}
