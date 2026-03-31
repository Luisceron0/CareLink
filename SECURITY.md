Security checklist (inicial)

Este archivo documenta las mitigaciones básicas requeridas por el proyecto.

-- Autenticación: JWT RS256 para emisión de access tokens; refresh tokens HttpOnly.
-- Hashing de contraseñas: Argon2id recomendado.
-- PHI: cifrado en aplicación con AES-256-GCM y claves por tenant (Supabase Vault).
- Logging: sanitizar PHI en logs operacionales; incluir trace_id y tenant_id (hashed).
- OWASP: aplicar validación de entrada con Bean Validation / Pydantic; evitar concatenación en queries.
-- Dependencias: escaneo con `mvn dependency-check`, `pip-audit` y Gitleaks en CI.

Referencias y checklist completo en `docs/SECURITY.md` (por completar).

Actualización F4-T02 (clinical-service):

- PHI en `Patient` y `Encounter` cifrado en aplicación con AES-256-GCM
	y clave derivada por tenant.
- Registro de auditoría PHI en `phi_audit_log` usando adaptador
	dedicado insert-only.
- Permisos DB reforzados para `app_clinical_user`:
	`REVOKE UPDATE, DELETE` en `phi_audit_log`.
- Test de permisos verifica que `DELETE` es rechazado por la BD.

Actualización F4-T03 (clinical-service API):

- Endpoints clínicos validan `X-Tenant-Id`, `X-User-Id` y `X-User-Role`.
- Control de acceso por rol: `RECEPTIONIST` no puede leer encounters.
- Protección cross-tenant: acceso fuera de tenant devuelve `403`.
- Encuentros firmados son inmutables: update devuelve `409`.
- Errores al cliente son sanitizados e incluyen `request_id`.

Actualización F4-T05 (retención GDPR vs MinSalud):

- Solicitudes `EU + ERASURE` requieren confirmación explícita (`confirmed=true`)
	antes de ejecutar eliminación irreversible de identidad desacoplada.
- Solicitudes de pacientes `CO` retornan `200` con motivo legal de retención,
	sin borrar la HCE.
- Audit log GDPR sin PHI en logs operacionales: `request_type`,
	`jurisdiction`, `result`, `base_legal`, `timestamp`, `tenant_id_hashed`,
	`user_id_hashed`.
