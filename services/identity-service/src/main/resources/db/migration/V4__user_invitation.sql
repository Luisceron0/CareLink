-- V4 — Soporte de esquema para FR-ID-02 (invitación de usuarios, service_id, baja).
--
-- `service_id` es TEXT libre, no una FK a una tabla `services` — el SRS (§4) lo
-- describe como "departamento (ej. 'Urgencias', 'Consulta Externa')" sin definir
-- una entidad Service en ningún otro lado del dominio. Mismo criterio ya aplicado a
-- `diagnosis_cie10` en ClinicalEncounter: no normalizar un catálogo que nadie pidió
-- todavía. NULL es válido — no todo rol (ej. TENANT_ADMIN) necesita un service_id.
--
-- `active` reemplaza "borrar" como mecanismo de baja: FR-ID-02 exige retener el
-- historial de auditoría de un usuario desactivado permanentemente, y V1 ya puso
-- `ON DELETE RESTRICT` en la FK de `users` por esta misma razón (ver ese archivo).
-- Desactivar es un UPDATE de aplicación, nunca un DELETE.
ALTER TABLE users
    ADD COLUMN service_id TEXT,
    ADD COLUMN active     BOOLEAN NOT NULL DEFAULT TRUE;
