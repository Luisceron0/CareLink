package com.carelink.clinical.domain.port;

import java.util.UUID;

/**
 * Puerto de auditoría para operaciones de PHI.
 */
public interface AuditLogPort {

    /**
     * Registra una acción sobre PHI.
     *
     * @param tenantId tenant
     * @param actorUserId usuario actor
     * @param targetPatientId paciente objetivo
     * @param action acción de negocio
     */
    void recordPhiAccess(UUID tenantId,
                         UUID actorUserId,
                         UUID targetPatientId,
                         String action);
}
