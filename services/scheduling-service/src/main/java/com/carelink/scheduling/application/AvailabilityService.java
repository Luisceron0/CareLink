package com.carelink.scheduling.application;

import com.carelink.scheduling.domain.AvailabilityBlock;
import com.carelink.scheduling.domain.Physician;
import com.carelink.scheduling.domain.port.AvailabilityRepository;
import com.carelink.scheduling.domain.port.PhysicianRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Servicio de disponibilidad: lista y crea bloques de availability.
 */
@Service
public final class AvailabilityService {

    /** Repositorio de médicos. */
    private final PhysicianRepository physicianRepository;

    /** Repositorio de bloques de disponibilidad. */
    private final AvailabilityRepository availabilityRepository;

    /**
     * Constructor.
     *
     * @param physicianRepositoryArg repository de médicos
     * @param availabilityRepositoryArg repository de disponibilidad
     */
    public AvailabilityService(
            final PhysicianRepository physicianRepositoryArg,
            final AvailabilityRepository availabilityRepositoryArg) {
        this.physicianRepository = physicianRepositoryArg;
        this.availabilityRepository = availabilityRepositoryArg;
    }

        /**
         * Lista bloques de disponibilidad de un physician dentro del tenant.
         *
         * @param tenantId    identificador del tenant
         * @param physicianId identificador del physician
         * @return lista de bloques
         */
        public List<AvailabilityBlock> listAvailability(
            final UUID tenantId,
            final UUID physicianId) {
        final var physicianOpt = physicianRepository
            .findByTenantAndId(tenantId, physicianId);
        final Physician p = physicianOpt.orElseThrow(() ->
            new IllegalArgumentException("Physician not found or not in tenant")
        );
        return availabilityRepository.findByPhysicianId(p.id());
        }

    /**
     * Crea un bloque de disponibilidad validando pertenencia al tenant.
     *
     * @param tenantId identificador del tenant
     * @param block    bloque a crear
     * @return bloque creado
     */
    public AvailabilityBlock createAvailability(
            final UUID tenantId,
            final AvailabilityBlock block) {
        final UUID physicianId = block.physicianId();
        physicianRepository.findByTenantAndId(tenantId, physicianId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Physician not found or not in tenant"));
        return availabilityRepository.save(block);
    }
}
