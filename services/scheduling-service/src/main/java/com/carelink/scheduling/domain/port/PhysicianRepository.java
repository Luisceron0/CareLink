package com.carelink.scheduling.domain.port;

import com.carelink.scheduling.domain.Physician;

import java.util.Optional;
import java.util.UUID;

/**
 * Puerto de persistencia para médicos.
 */
public interface PhysicianRepository {

    /**
     * Busca un physician por tenant y id.
        *
        * @param tenantId    identificador del tenant
        * @param physicianId identificador del physician
        * @return optional con el physician si existe
        */
        Optional<Physician> findByTenantAndId(UUID tenantId,
                                       UUID physicianId);

    /**
     * Persiste un physician.
     *
     * @param physician objeto a persistir
     * @return physician persistido
     */
    Physician save(Physician physician);
}
