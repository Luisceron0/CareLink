package com.carelink.identity.domain.port;

import com.carelink.identity.domain.Tenant;
import java.util.Optional;
import java.util.UUID;

public interface TenantRepository {
    Optional<Tenant> findBySlug(String slug);

    /**
     * El JWT lleva {@code tenant_id} (UUID) en su claim, no el slug (ver
     * AuthenticatedPrincipal) — cualquier caso de uso que necesite el slug del tenant
     * del request autenticado para operar sobre su schema (Patient, y todo lo que siga)
     * pasa por acá.
     */
    Optional<Tenant> findById(UUID id);

    void save(Tenant tenant);
}
