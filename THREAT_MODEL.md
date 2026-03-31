THREAT MODEL — inicio

Superficie de ataque nueva (resumen):
- Endpoints de registro y login (public-facing)
- Proceso de provisionamiento de tenant (creación de esquemas DB)
- Exportación y manejo de PHI (lectura/descarga)

Amenazas identificadas y mitigaciones (inicio):
- Registro masivo / abuso → rate limiting por IP + captcha opcional
- Inyección SQL en queries dinámicas → uso de queries parametrizadas y PReparedStatements
- Acceso cross-tenant → validar tenant_id desde JWT en cada request
- Exposición de PHI en logs → sanitizar logs y evitar imprimir campos PHI

¿Requiere update del THREAT_MODEL global? SÍ — expandir con vectores por servicio y mitigaciones técnicas.

Notas: este documento es un starter; cada feature añadirá sus vectores específicos.

---

JWT / Refresh Tokens / JWKS (añadido)

Superficie de ataque nueva:
- Exposición de clave privada (config/secret leak)
- Manipulación de JWKS (MITM o JWKS comprometido)
- Replay de refresh tokens si no se rotan correctamente
- Algoritmo‑confusion (tokens con `alg:none` o cambio a HS256)

Amenazas y mitigaciones:
- Clave privada expuesta → Mitigación: almacenar claves privadas en Supabase Vault/KMS; acceso limitado por rol; auditoría de accesos; no incluir claves en repositorios ni en variables de entorno en producción.
- JWKS malicioso / MITM → Mitigación: solo fetch desde URLs HTTPS validadas, validar issuer/audience, soportar pinning opcional (thumbprint) y cache con TTL; monitorizar cambios inusuales en JWKS.
- Replay de refresh tokens → Mitigación: almacenar refresh tokens hasheados (HMAC-SHA256), rotación on‑use (issue new + revoke old), expiración corta y limite concurrent sessions; auditar refresh/issue events.
- Algoritmo confusion → Mitigación: aceptar solo RS256 en verificación; rechazar tokens con `alg` distinto o con missing `kid` when required.

Operaciones y requisitos de despliegue:
- `.env.example` debe documentar variables: `JWT_ACCESS_TTL`, `JWT_JWKS_URL`, `REFRESH_TOKEN_HMAC_SECRET`, `REFRESH_TOKEN_TTL_SECONDS`, `MAX_CONCURRENT_SESSIONS`.
- Implementar adaptador `VaultKeyProvider` que lea la clave privada directamente desde Vault para producción.
- Añadir alertas en monitoring si la lista de `kid` cambia inesperadamente o si verificaciones fallan masivamente.

---

Scheduling / Availability (nueva superficie)

Superficie de ataque nueva:
- Endpoints de disponibilidad: `POST /api/v1/physicians/{id}/availability`, `GET /api/v1/physicians/{id}/availability` (public para tenants autenticados)
- Inputs: `tenant_id`, `physician_id`, `day`, `start_time`, `end_time`

Amenazas y mitigaciones:
- Acceso cross-tenant → Mitigación: validar `tenant_id` extraído del JWT en la capa de aplicación (`AvailabilityService`) antes de cualquier llamada a repositorios; tests automáticos que prueban acceso cross-tenant -> 403/401.
- Falsificación/escrow de disponibilidad (usuario crea bloques para otro doctor) → Mitigación: ownership enforced en `AvailabilityService` y en queries JPA (`findByIdAndTenantId`), validación adicional en controladores y pruebas.
- Condiciones de carrera / double-booking → Mitigación: usar transacciones y optimistic locking en entidades críticas (añadir `@Version` si es necesario para `TimeSlot`/`Appointment`), aplicar verificación de conflictos en `SlotCalculator`/caso de uso y pruebas de concurrencia con zonky embedded DB.
 - Condiciones de carrera / double-booking → Mitigación: usar transacciones y optimistic locking en entidades críticas (añadir `@Version` si es necesario para `TimeSlot`/`Appointment`), aplicar verificación de conflictos en `SlotCalculator`/caso de uso y pruebas de concurrencia con zonky embedded DB.
	- Nota: para `appointments` se añade validación adicional en el dominio y el caso de uso `BookAppointmentUseCase` que detecta conflictos y sugiere alternativas. En F3-T02 se implementará `@Version` en la entidad JPA y una constraint única `(physician_id, slot_start) WHERE status = BOOKED`.
	- Eventos: los eventos de appointment usan JSON Schema y sólo incluyen `patient_id` como UUID (sin nombre ni datos PHI) para reducir exposición en transit y en topics.
- Input malformed / timezone bugs → Mitigación: validar formatos y rangos (Bean Validation), normalizar zonas horarias en server (UTC) y documentar en API.
- DoS en endpoints de escritura (spam de creación de bloques) → Mitigación: rate limiting por tenant/user e instrumentación de métricas y alertas para picos.
- Tampering de datos en transporte → Mitigación: exigir HTTPS y validar JWTs firmados con RS256; asegurar cabeceras CORS apropiadas.

Operaciones y requisitos de despliegue:
- Añadir pruebas de integración con `@AutoConfigureEmbeddedDatabase(provider = DatabaseProvider.ZONKY)` para validar comportamiento real de Postgres sin Docker.
- Asegurar que los adaptadores JPA estén en `infrastructure/persistence/` y que el `AvailabilityService` no importe infraestructura.
- Auditoría: registrar cambios de disponibilidad (create/update/delete) con trace_id, tenant_id (hashed) y user_id (hashed); los logs no deben contener PHI.

---

Scheduling / Appointments (Fase 3)

Superficie de ataque nueva:
- Endpoints de citas: `POST/GET/PATCH/DELETE /api/v1/appointments`
- Validación por rol de transiciones de estado
- Publicación de eventos de cita en Kafka (`appointments`)

Amenazas y mitigaciones:
- Double booking por concurrencia alta -> `@Version` (optimistic locking)
	+ constraint único parcial para slot activo por médico.
- Acceso cross-tenant -> queries siempre por `tenant_id` y cabecera
	`X-Tenant-Id` validada en cada operación.
- Escalada de privilegios en cambios de estado -> control por rol:
	`PHYSICIAN` para `IN_PROGRESS/COMPLETED`; `RECEPTIONIST`/`TENANT_ADMIN`
	para cancelación.
- Exposición de PHI en eventos -> los eventos sólo publican IDs
	(`patient_id` UUID) y metadatos clínicamente neutros.

Notas operativas:
- Topic Kafka: `appointments`
- Eventos emitidos: `AppointmentBooked`, `AppointmentCancelled`,
	`AppointmentStatusChanged`

---

Clinical / PHI Encryption + Audit (F4-T02)

Superficie de ataque nueva:
- Campos PHI persistidos por `clinical-service` (pacientes y encuentros).
- Tabla de auditoría `phi_audit_log` para lecturas/escrituras de PHI.
- Flujo de descifrado en lectura de paciente dentro de la capa aplicación.

Amenazas y mitigaciones:
- Fuga de PHI por lectura directa de BD -> cifrado AES-256-GCM por tenant
	en aplicación antes de persistir (`EncryptionPort`).
- Manipulación de evidencia de auditoría -> tabla insert-only con
	`REVOKE UPDATE, DELETE` para `app_clinical_user`.
- Exposición accidental en respuestas/logs -> solo se auditan metadatos
	(`tenant_id`, actor, paciente, acción, timestamp), sin dump de PHI.
- Omisión de auditoría en lectura -> caso de uso de lectura (`GetPatientUseCase`)
	registra `PATIENT_READ` exactamente una vez por acceso exitoso.

Notas operativas:
- Variable requerida para entorno local: `CLINICAL_MASTER_KEY_BASE64`
	(32 bytes en base64).
- ADR asociada: `docs/adr/ADR-005.md`.

---

Clinical / API Patients + Encounters (F4-T03)

Superficie de ataque nueva:
- Endpoints CRUD clínicos y de export: `/api/v1/patients/*`.
- Operaciones de firma de encuentro y bloqueo por inmutabilidad.
- Exportación de PHI por PDF y FHIR JSON.

Amenazas y mitigaciones:
- Acceso cross-tenant por manipulación de ids -> validación estricta de
	`X-Tenant-Id` y verificación de ownership por recurso.
- Lectura de encounter por rol no autorizado -> `RECEPTIONIST` bloqueado
	con respuesta `403` en endpoints de encounters.
- Modificación de encuentro firmado -> excepción de inmutabilidad mapeada a
	`409 Conflict`.
- Exposición de PHI en errores -> respuestas sanitizadas con `request_id` y
	sin stack traces ni valores PHI.
- Exportación sin trazabilidad -> auditoría obligatoria para
	`PATIENT_EXPORT_PDF` y `PATIENT_EXPORT_FHIR`.

Notas operativas:
- Tests de integración cubren:
	`403` en lectura de encounter por `RECEPTIONIST`,
	`403` para acceso cross-tenant,
	`409` en update de encounter firmado.

---

Clinical / GDPR Retention Resolution (F4-T05)

Superficie de ataque nueva:
- Endpoint `POST /api/v1/patients/{id}/gdpr-request` con decisiones legales
	por jurisdicción (`EU`, `CO`).
- Flujo de eliminación irreversible de identidad desacoplada para pacientes EU.

Amenazas y mitigaciones:
- Borrado sin confirmación explícita -> para `EU + ERASURE` se exige
	`confirmed=true`; sin confirmación retorna `400`.
- Supresión indebida de HCE en Colombia -> para `CO` nunca se elimina HCE;
	se responde `200` con base legal de retención (FR-CLN-06).
- Exposición de PHI en observabilidad -> auditoría GDPR registra solo
	metadatos (`request_type`, `jurisdiction`, `result`, `base_legal`,
	`timestamp`, `tenant_id_hashed`, `user_id_hashed`).
- Riesgo de acoplar identidad y clínica en EU -> store de identidad separado;
	la HCE se conserva seudonimizada.

Notas operativas:
- `InMemoryGdprIdentityStore` es `// TEST ONLY` para pruebas/local.
- Producción requiere adaptador real a Supabase Vault.


