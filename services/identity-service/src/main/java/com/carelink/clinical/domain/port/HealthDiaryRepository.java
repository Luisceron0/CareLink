package com.carelink.clinical.domain.port;

import com.carelink.clinical.domain.HealthDiaryEntry;
import com.carelink.clinical.domain.InterventionOutcome;
import com.carelink.clinical.domain.value.ServiceScope;
import com.carelink.identity.domain.value.TenantSlug;

import java.util.Optional;
import java.util.UUID;

public interface HealthDiaryRepository {

    /** Guarda la entrada con sus signos vitales e intervenciones, en una sola transacción. */
    void save(TenantSlug tenantSlug, HealthDiaryEntry entry);

    Optional<HealthDiaryEntry> findById(TenantSlug tenantSlug, UUID entryId, ServiceScope scope);

    /** FR-CLN-05 — registra el resultado NOC de una intervención ya ejecutada. false si no existe o no es del scope. */
    boolean recordOutcome(TenantSlug tenantSlug, UUID interventionId, InterventionOutcome outcome, ServiceScope scope);
}
