package com.carelink.identity.domain;

import com.carelink.identity.domain.value.Email;
import com.carelink.identity.domain.value.HashedPassword;
import java.time.OffsetDateTime;

/**
 * {@code serviceId} es texto libre y nullable — no todo rol lo necesita
 * (p. ej. {@code TENANT_ADMIN}), y no existe una entidad {@code Service} en el
 * dominio (§4 del SRS lo describe como "Urgencias", "Consulta Externa" sin más
 * estructura). {@code active} es el mecanismo de baja de FR-ID-02: nunca se borra
 * un usuario (la FK de {@code users} es {@code ON DELETE RESTRICT} a propósito),
 * se desactiva, para que su historial de auditoría se retenga permanentemente.
 */
public record User(java.util.UUID id, java.util.UUID tenantId, Email email, String role, String serviceId,
                    boolean active, HashedPassword password, OffsetDateTime createdAt) {}
