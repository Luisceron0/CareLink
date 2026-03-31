package com.carelink.scheduling.domain.port;

import com.carelink.scheduling.domain.AvailabilityBlock;

import java.util.List;
import java.util.UUID;

/**
 * Puerto de persistencia para bloques de disponibilidad.
 */
public interface AvailabilityRepository {

    /**
     * Busca bloques por physician.
        *
        * @param physicianId identificador del physician
        * @return lista de bloques de disponibilidad
        */
        List<AvailabilityBlock> findByPhysicianId(UUID physicianId);

    /**
     * Persiste un bloque de disponibilidad.
     *
     * @param block bloque a persistir
     * @return bloque persistido con id
     */
    AvailabilityBlock save(AvailabilityBlock block);
}
