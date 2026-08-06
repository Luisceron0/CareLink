package com.carelink.clinical.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * FR-CLN-11 — "el flag de valor crítico debe disparar notificación al médico
 * solicitante". §16.4 deja fuera email/SMS en este milestone, así que la notificación es
 * una FILA que el médico consulta, no un side effect que se pierde si nadie estaba
 * mirando en ese momento. {@code acknowledgedAt} nulo la mantiene pendiente: es una
 * obligación abierta, no un mensaje que pasó.
 */
public record CriticalValueNotification(
        UUID id,
        UUID labOrderId,
        UUID patientId,
        UUID notifyUserId,
        OffsetDateTime createdAt,
        OffsetDateTime acknowledgedAt,
        String serviceId
) {
    public boolean isPending() {
        return acknowledgedAt == null;
    }
}
