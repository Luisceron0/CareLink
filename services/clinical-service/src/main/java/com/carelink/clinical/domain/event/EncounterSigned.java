package com.carelink.clinical.domain.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Evento emitido al firmar un encuentro.
 *
 * @param tenantId tenant
 * @param encounterId encuentro firmado
 * @param physicianId médico firmante
 * @param occurredAt instante del evento
 */
public record EncounterSigned(UUID tenantId,
                              UUID encounterId,
                              UUID physicianId,
                              Instant occurredAt) {
}
