package com.carelink.clinical.domain.port;

import com.carelink.clinical.domain.Prescription;
import com.carelink.clinical.domain.value.ServiceScope;
import com.carelink.identity.domain.value.TenantSlug;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PrescriptionRepository {
    void save(TenantSlug tenantSlug, Prescription prescription);

    Optional<Prescription> findById(TenantSlug tenantSlug, UUID id, ServiceScope scope);

    /** FR-CLN-09 — todas las prescripciones que cuelgan de un encounter, incluidas las de interconsulta. */
    List<Prescription> findByEncounter(TenantSlug tenantSlug, UUID encounterId, ServiceScope scope);
}
