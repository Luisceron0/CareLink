package com.carelink.scheduling.infrastructure.web;

import com.carelink.scheduling.application.AvailabilityService;
import com.carelink.scheduling.domain.AvailabilityBlock;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Controlador REST para bloques de disponibilidad.
 */
@RestController
@RequestMapping("/api/v1/physicians/{physicianId}/availability")
public final class AvailabilityController {

    /** Servicio de disponibilidad. */
    private final AvailabilityService service;
    /**
     * Constructor.
     *
     * @param serviceArg servicio de disponibilidad
     */
    public AvailabilityController(final AvailabilityService serviceArg) {
        this.service = serviceArg;
    }

        /**
         * Lista disponibilidad para un physician.
         *
         * @param tenantId    tenant del request
         * @param physicianId identificador del physician
         * @return response con la lista de bloques
         */
        @GetMapping
        public ResponseEntity<List<AvailabilityBlock>> list(
            @RequestHeader("X-Tenant-Id") final UUID tenantId,
            @PathVariable final UUID physicianId) {
        return ResponseEntity.ok(
            service.listAvailability(tenantId, physicianId)
        );
        }

    /**
     * Crea un bloque de disponibilidad validando tenant y physician.
     *
     * @param tenantId    tenant del request
     * @param physicianId identifier del physician en la ruta
     * @param block       payload del bloque a crear
     * @return response con el bloque creado
     */
    @PostMapping
    public ResponseEntity<AvailabilityBlock> create(
            @RequestHeader("X-Tenant-Id") final UUID tenantId,
            @PathVariable final UUID physicianId,
            @RequestBody final AvailabilityBlock block) {
        if (!physicianId.equals(block.physicianId())) {
            return ResponseEntity.badRequest().build();
        }
        final AvailabilityBlock created =
                service.createAvailability(tenantId, block);
        return ResponseEntity.ok(created);
    }
}
