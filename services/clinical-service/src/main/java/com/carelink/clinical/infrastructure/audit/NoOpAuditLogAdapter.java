package com.carelink.clinical.infrastructure.audit;

import com.carelink.clinical.domain.port.AuditLogPort;

import java.util.UUID;

/**
 * Adaptador no-op de auditoria para entornos sin datasource.
 */
public final class NoOpAuditLogAdapter implements AuditLogPort {

    @Override
    public void recordPhiAccess(final UUID tenantId,
                                final UUID actorUserId,
                                final UUID targetPatientId,
                                final String action) {
        // No-op en entorno local sin persistencia de auditoria.
    }
}
