package com.carelink.clinical.domain.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Evento emitido al registrar paciente.
 *
 * @param tenantId tenant del paciente
 * @param patientId identificador de paciente
 * @param occurredAt instante del evento
 */
public record PatientRegistered(UUID tenantId,
                                UUID patientId,
                                Instant occurredAt) {
}
