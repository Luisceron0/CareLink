package com.carelink.clinical.domain.port;

import com.carelink.clinical.domain.Encounter;

import java.util.Optional;
import java.util.UUID;

/**
 * Puerto de persistencia de encuentros clínicos.
 */
public interface EncounterRepository {

    /**
     * Guarda un encuentro clínico.
     *
     * @param encounter encuentro
     * @return encuentro persistido
     */
    Encounter save(Encounter encounter);

    /**
     * Busca encuentro por tenant e id.
     *
     * @param tenantId tenant
     * @param encounterId encuentro
     * @return encuentro si existe
     */
    Optional<Encounter> findByTenantAndId(UUID tenantId, UUID encounterId);
}
