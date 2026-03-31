# CareLink — Project Plan

**Versión:** 1.0  
**Organización:** Features verticales completos  
**Verificación humana:** Al terminar cada fase + ante cada decisión arquitectónica nueva  
**Referencia:** docs/SRS.md  

---

## Cómo usar este documento

Copilot lee este archivo al inicio de cada sesión de trabajo.
Las tareas se ejecutan en orden. No se salta una tarea sin marcarla como
`[x]` o `[SKIPPED: razón]`.

**Copilot no necesita pedir confirmación humana entre tareas de la misma fase.**
Sí debe detenerse y notificar cuando:
- Termina una fase completa → escribe `## CHECKPOINT: Fase N completada` al final
  de esta sección y espera revisión
- Encuentra una decisión arquitectónica no cubierta en el SRS o los ADRs existentes
  → escribe `## DECISION REQUIRED: [descripción]` y espera

Para cada tarea, Copilot debe:
1. Ejecutar el Protocolo Obligatorio del `copilot-instructions.md` internamente
2. Aplicar el security checklist antes de marcar `[x]`
3. Ejecutar los tests y linter del servicio afectado
4. Hacer commit con el formato convencional antes de pasar a la siguiente tarea

---

## Estado general

| Fase | Nombre | Estado | Checkpoint |
|------|--------|--------|-----------|
| 0 | Fundación del repositorio | `[ ] Pendiente` | — |
| 1 | Registro de tenant y autenticación | `[EN PROGRESO]` | Requiere revisión |
| 2 | Gestión de disponibilidad médica | `[x] Completada` | Requiere revisión |
| 3 | Reserva de cita completa | `[ ] Pendiente` | Requiere revisión |
| 4 | Historia clínica electrónica | `[ ] Pendiente` | Requiere revisión |
| 5 | Facturación y RIPS | `[ ] Pendiente` | Requiere revisión |
| 6 | Notificaciones y recordatorios | `[ ] Pendiente` | Requiere revisión |
| 7 | Portal del paciente | `[ ] Pendiente` | Requiere revisión |
| 8 | Reportes y auditoría | `[ ] Pendiente` | Requiere revisión |
| 9 | Observabilidad e infraestructura | `[ ] Pendiente` | Requiere revisión |

---

## FASE 0 — Fundación del repositorio

**Objetivo:** estructura base del monorepo, configuración de herramientas,
esquema de BD inicial, variables de entorno. Sin lógica de negocio todavía.
**Checkpoint:** no requiere revisión humana — es configuración pura.

### F0-T01 — Estructura del monorepo
- [ ] Crear estructura de directorios según `copilot-instructions.md`
- [ ] Crear `.gitignore` para Java, Python, Node, Terraform, `.env`
- [ ] Crear `README.md` con overview, arquitectura ASCII del SRS y quick start
- [ ] Crear `.env.example` con todas las variables requeridas documentadas
- [ ] Crear `SECURITY.md` con mitigaciones OWASP 2025 (estructura inicial)
- [ ] Crear `THREAT_MODEL.md` con amenazas del SRS sección 8.1

**Commit:** `chore: Initialize monorepo structure`

### F0-T02 — Configuración de servicios Java
- [ ] Crear `pom.xml` raíz con módulos Maven para los 4 servicios Spring Boot
- [ ] Configurar Spring Boot 3.3 + Java 21 en cada servicio
- [ ] Configurar Checkstyle con reglas de estilo del proyecto
- [ ] Configurar Spring Security base (sin reglas aún)
- [ ] Agregar Zonky Embedded Database (`io.zonky.test:embedded-database-spring-test`)
  en scope test — reemplaza Testcontainers para PostgreSQL
- [ ] Agregar `spring-kafka-test` con `@EmbeddedKafka` — reemplaza Testcontainers para Kafka
- [ ] Verificar: `./mvnw test` pasa en los 4 servicios sin Docker ni servicios externos

**Commit:** `chore(java): Configure Spring Boot services with Zonky and EmbeddedKafka`

### F0-T03 — Configuración de Notification Service (Python)
- [ ] Crear `pyproject.toml` con FastAPI, Pydantic v2, pytest, ruff, mypy
- [ ] Crear estructura `app/` con `main.py`, `config.py`, `models/`, `routes/`, `services/`
- [ ] Configurar ruff y mypy
- [ ] Verificar: `pytest`, `ruff check .`, `mypy app/` pasan sin errores

**Commit:** `chore(notification): Configure FastAPI service`

### F0-T04 — Configuración de portales Next.js
- [ ] Crear `physician-portal/` con Next.js 14 App Router + TypeScript strict
- [ ] Crear `patient-portal/` con Next.js 14 App Router + TypeScript strict
- [ ] Configurar ESLint + Prettier + i18next + next-intl en ambos
- [ ] Configurar locales: `es-CO` y `en-US` con archivos de mensajes vacíos
- [ ] Verificar: `npm run type-check` y `npm run lint` pasan en ambos

**Commit:** `chore(portals): Configure Next.js portals with i18n`

### F0-T05 — Esquema de base de datos inicial
- [ ] Crear `migrations/001_public_schema.sql` con tablas: `tenants`, `users`, `sessions`
- [ ] Crear `migrations/002_tenant_schema_template.sql` con todas las tablas del SRS
  sección 10.2 (template que se aplica al crear cada tenant)
- [ ] Aplicar constraints críticos del SRS sección 10.3
- [ ] Crear script `scripts/create_tenant_schema.sql` que instancia el template
- [ ] Verificar: migrations corren sin error en PostgreSQL local

**Commit:** `feat(db): Add initial schema migrations and tenant template`

### F0-T06 — Pipeline CI/CD base
- [ ] Crear `.github/workflows/ci.yml` con runners nativos (sin Docker):
  - `actions/setup-java@v4` para servicios Java
  - `actions/setup-python@v5` para Notification Service
  - Semgrep (binario nativo), pip-audit, mvn dependency-check, Gitleaks
  - Tests Java con Zonky + @EmbeddedKafka (sin servicios externos)
  - Tests Python con pytest
  - ESLint para Next.js
- [ ] Crear `.semgrep/no-weak-crypto.yaml` — bloquea MD5, SHA1
- [ ] Crear `.semgrep/no-string-sql.yaml` — bloquea concatenación en queries
- [ ] Crear `.semgrep/phi-audit-required.yaml` — detecta acceso PHI sin audit log
- [ ] Verificar: pipeline pasa en un PR de prueba sin Docker

**Commit:** `ci: Add base pipeline with native runners, no Docker`

---

## FASE 1 — Registro de tenant y autenticación

**Objetivo:** un nuevo consultorio puede registrarse, verificar su email,
invitar usuarios y autenticarse con MFA. Es el feature vertical más importante
porque todo lo demás depende de él.
**Checkpoint:** requiere revisión humana al completar.
**Servicios:** `identity-service` + `physician-portal` (páginas de auth)

### F1-T01 — Dominio de Identity (capas domain/ y application/) `[EN PROGRESO]`
> Protocolo Obligatorio ejecutado y aprobado — Copilot puede proceder sin confirmación adicional.
> Threat model actualizado: vector de provisión de schema y validación de tax_id documentados.
> ADR-007 añadido a THREAT_MODEL.md requerido al completar esta tarea.

- [ ] Crear entidades de dominio: `Tenant`, `User`, `Session` como Java records
- [ ] Crear value objects: `Email`, `TaxId`, `TenantSlug`, `HashedPassword`
- [ ] Crear puertos (interfaces): `TenantRepository`, `UserRepository`,
  `SessionRepository`, `EmailNotifier`, `SchemaProvisioner`
- [ ] Crear casos de uso: `RegisterTenantUseCase`, `VerifyEmailUseCase`,
  `LoginUseCase`, `RefreshTokenUseCase`, `LogoutUseCase`
- [ ] Crear excepciones de dominio: `TenantAlreadyExistsException`,
  `InvalidTaxIdException`, `EmailNotVerifiedException`, `AccountLockedException`
- [ ] Verificar: 100% de cobertura en domain/ con tests unitarios puros (sin Spring)
- [ ] Actualizar `THREAT_MODEL.md` con vector de provisión de schema y validación de tax_id

**Commit:** `feat(identity): Add tenant and user domain model with ports`

### F1-T02 — Infraestructura de Identity
- [ ] Implementar `JpaTenantRepository` adaptando el puerto de dominio
- [ ] Implementar `JpaUserRepository` y `JpaSessionRepository`
- [ ] Implementar `PostgresSchemaProvisioner` — crea schema del tenant en la BD
  ejecutando el template de `migrations/002_tenant_schema_template.sql`
- [ ] Implementar `SmtpEmailNotifier` para email de verificación
- [ ] Implementar `Argon2PasswordEncoder` (argon2-cffi wrapper)
- [ ] Test de integración con Zonky Embedded Database: provisión de schema real en PostgreSQL
  sin Docker — usa `@AutoConfigureEmbeddedDatabase(provider = DatabaseProvider.ZONKY)`

**Commit:** `feat(identity): Add infrastructure adapters for tenant provisioning`

### F1-T03 — API de registro y verificación
- [ ] Crear `POST /api/v1/auth/register` — valida NIT/tax_id por país, crea tenant
  y TENANT_ADMIN en transacción, provisiona schema, envía email de verificación
- [ ] Crear `GET /api/v1/auth/verify-email?token=` — activa el tenant
- [ ] Crear filtro de Spring Security para rutas públicas vs. protegidas
- [ ] Implementar generación y validación de JWT (RS256, 15 min) + refresh token
  (7 días, HttpOnly cookie, rotado en cada uso)
- [ ] Security: rate limiting en `/register` — 5 intentos por IP por hora
- [ ] Test: registro duplicado devuelve 409
- [ ] Test: registro sin tax_id válido devuelve 400
- [ ] Test: schema del tenant existe en PostgreSQL después del registro exitoso
- [ ] Test: email de verificación se envía (mock del SMTP)

**Commit:** `feat(identity): Add tenant registration and email verification endpoints`

### F1-T04 — Login con MFA
- [ ] Crear `POST /api/v1/auth/login` — email + password, devuelve access token
  o challenge MFA si el rol lo requiere
- [ ] Crear `POST /api/v1/auth/mfa/setup` — genera secret TOTP, devuelve QR
- [ ] Crear `POST /api/v1/auth/mfa/verify` — verifica código TOTP, completa login
- [ ] Crear `POST /api/v1/auth/refresh` — rota refresh token, emite nuevo access token
- [ ] Crear `POST /api/v1/auth/logout` — revoca refresh token en BD
- [ ] Security: lockout tras 5 intentos fallidos — 15 min + email de alerta
- [ ] Security: MFA obligatorio para PHYSICIAN y TENANT_ADMIN (verificado en filtro)
- [ ] Test: 6to intento fallido devuelve 429 y activa lockout
- [ ] Test: JWT expirado devuelve 401
- [ ] Test: PHYSICIAN sin MFA configurado no puede acceder a endpoints protegidos

**Commit:** `feat(identity): Add MFA login flow with brute force protection`

### F1-T05 — Gestión de usuarios del tenant
- [ ] Crear `GET /api/v1/users` — lista usuarios del tenant autenticado
- [ ] Crear `POST /api/v1/users/invite` — invita usuario por email con rol asignado
- [ ] Crear `PATCH /api/v1/users/:id` — actualiza rol o desactiva cuenta
- [ ] Security: solo TENANT_ADMIN puede gestionar usuarios
- [ ] Security: TENANT_ADMIN no puede desactivar su propia cuenta
- [ ] Test: PHYSICIAN intentando invitar usuario devuelve 403
- [ ] Test: usuario desactivado no puede autenticarse

**Commit:** `feat(identity): Add user management endpoints for TENANT_ADMIN`

### F1-T06 — Páginas de auth en Physician Portal
- [ ] Crear página `/login` con form de email + password
- [ ] Crear página `/mfa` con input de código TOTP
- [ ] Crear página `/register` con form de registro de clínica
- [ ] Crear página `/verify-email` que consume el token de la URL
- [ ] Implementar middleware de Next.js que redirige a `/login` si no hay sesión válida
- [ ] i18n: todos los textos en `es-CO` y `en-US`
- [ ] Test Playwright: flujo completo registro → verificación → login → MFA

**Commit:** `feat(physician-portal): Add authentication pages with i18n`

### F1-T07 — ADR y documentación de la fase
- [ ] Crear `docs/adr/ADR-001.md` — schema-per-tenant vs row-level tenancy
- [ ] Crear `docs/adr/ADR-002.md` — RS256 vs HS256 para JWT
- [ ] Crear `docs/adr/ADR-003.md` — Argon2id para hashing de contraseñas
- [ ] Actualizar `docs/API.md` con todos los endpoints de esta fase
- [ ] Actualizar `SECURITY.md` con mitigaciones A01, A02, A04, A07 implementadas

**Commit:** `docs(identity): Add ADRs and API documentation for auth phase`

**>> CHECKPOINT FASE 1 — Esperar revisión humana antes de continuar <<**

---

## FASE 2 — Disponibilidad médica

**Objetivo:** un médico puede configurar su agenda y una recepcionista puede
consultar slots disponibles en tiempo real.
**Checkpoint:** requiere revisión humana al completar.
**Servicios:** `scheduling-service` + `physician-portal`

### F2-T01 — Dominio de Scheduling
- [ ] Crear entidades: `Physician`, `AvailabilityBlock`, `BlockedPeriod`, `TimeSlot`
- [ ] Crear value objects: `SlotDuration`, `WorkingHours`, `DateRange`
- [ ] Crear puertos: `PhysicianRepository`, `AvailabilityRepository`,
  `SlotCalculator`
- [ ] Crear caso de uso: `ConfigureAvailabilityUseCase`, `QueryAvailableSlotsUseCase`
- [ ] Regla de dominio: slots se calculan dinámicamente — no se almacenan pre-generados
- [ ] Regla de dominio: slot duration configurable por médico (15/20/30/45/60 min)
- [ ] Test unitario: generación correcta de slots para una semana dada configuración

**Commit:** `feat(scheduling): Add availability domain model and slot calculator`

### F2-T02 — Infraestructura y API de disponibilidad
- [ ] Implementar `JpaPhysicianRepository` y `JpaAvailabilityRepository`
- [ ] Crear `GET /api/v1/physicians` — lista médicos del tenant
- [ ] Crear `POST /api/v1/physicians/:id/availability` — configura bloques de trabajo
- [ ] Crear `POST /api/v1/physicians/:id/blocked-periods` — registra ausencias
- [ ] Crear `GET /api/v1/physicians/:id/slots?date=&range=` — slots disponibles
- [ ] Security: TENANT_ADMIN y PHYSICIAN pueden configurar disponibilidad
- [ ] Security: RECEPTIONIST solo puede consultar, no configurar
- [ ] Performance: query de slots debe responder < 300ms (test con k6 básico)
- [ ] Test: slot query respeta timezone del tenant
- [ ] Test: blocked period excluye correctamente los slots del rango

**Commit:** `feat(scheduling): Add availability configuration and slot query endpoints`

### F2-T03 — Vista de agenda en Physician Portal
- [x] Crear página `/schedule` con vista de calendario semanal
- [x] Mostrar slots disponibles y bloqueados por color
- [x] Formulario de configuración de horario por día de semana
- [x] i18n: formato de fecha y hora según locale del usuario
- [x] Test Playwright: médico configura horario y ve los slots generados

**Commit:** `feat(physician-portal): Add weekly schedule view with availability config`

## CHECKPOINT: Fase 2 completada

---

## FASE 3 — Reserva de cita completa

**Objetivo:** una recepcionista puede reservar una cita sin colisiones,
con resolución de conflictos y cambio de estado completo.
**Checkpoint:** requiere revisión humana al completar.
**Servicios:** `scheduling-service` + `physician-portal`

### F3-T01 — Dominio de Appointment
- [x] Crear entidad `Appointment` con campo `version` para optimistic locking
- [x] Crear states: `PENDING → CONFIRMED → IN_PROGRESS → COMPLETED | CANCELLED | NO_SHOW`
- [x] Crear value object `AppointmentStatus` con validación de transiciones permitidas
- [x] Crear excepciones: `SlotAlreadyBookedException` (incluye 3 alternativas),
  `InvalidStatusTransitionException`
- [x] Crear eventos de dominio: `AppointmentBooked`, `AppointmentCancelled`,
  `AppointmentCompleted`, `AppointmentNoShow`
- [x] Crear caso de uso: `BookAppointmentUseCase`, `UpdateAppointmentStatusUseCase`,
  `CancelAppointmentUseCase`
- [x] Test unitario: transiciones de estado inválidas lanzan excepción
- [x] Test unitario: `BookAppointmentUseCase` emite evento `AppointmentBooked`

**Commit:** `feat(scheduling): Add appointment domain with state machine and events`

### F3-T02 — Optimistic locking y constraint de BD
- [x] Agregar `@Version` a entidad `Appointment` en JPA
- [x] Agregar constraint único en BD: `(physician_id, slot_start) WHERE status = BOOKED`
- [x] Implementar manejo de `ObjectOptimisticLockingFailureException` →
  captura, busca 3 alternativas, lanza `SlotAlreadyBookedException`
- [x] Test de concurrencia con @EmbeddedKafka + Zonky: 10 threads intentando reservar el mismo
  slot → exactamente 1 éxito, 9 `SlotAlreadyBookedException` — sin Docker
- [x] Crear `docs/adr/ADR-004.md` — optimistic locking vs. pessimistic locking

**Commit:** `feat(scheduling): Add optimistic locking with conflict resolution`

### F3-T03 — API de citas
- [x] Crear `POST /api/v1/appointments` — reserva cita, emite `AppointmentBooked` a Kafka
- [x] Crear `GET /api/v1/appointments` — lista con filtros (fecha, médico, estado)
- [x] Crear `PATCH /api/v1/appointments/:id/status` — transición de estado
- [x] Crear `DELETE /api/v1/appointments/:id` — cancela (soft delete, emite evento)
- [x] Publicar eventos a Kafka en cada transición: topic `appointments`
- [x] Security: RECEPTIONIST puede reservar y cancelar, no puede marcar IN_PROGRESS
- [x] Security: PHYSICIAN puede marcar IN_PROGRESS y COMPLETED
- [x] Test: respuesta de conflicto incluye exactamente 3 slots alternativos
- [x] Test: cross-tenant appointment access devuelve 403

**Commit:** `feat(scheduling): Add appointment CRUD endpoints with Kafka events`

### F3-T04 — UI de reserva en Physician Portal
- [x] Crear página `/appointments/new` — selector de médico, fecha, slot disponible
- [x] Mostrar slots en tiempo real actualizados por polling cada 30s
- [x] Mostrar modal de conflicto con 3 alternativas cuando hay colisión
- [x] Crear página `/appointments` — lista con filtros y cambio de estado inline
- [x] i18n: fechas, horas y mensajes de error localizados
- [x] Test Playwright: flujo completo de reserva incluyendo resolución de conflicto

**Commit:** `feat(physician-portal): Add appointment booking UI with conflict resolution`

## CHECKPOINT: Fase 3 completada

**>> CHECKPOINT FASE 3 — Esperar revisión humana antes de continuar <<**

---

## FASE 4 — Historia clínica electrónica (HCE)

**Objetivo:** un médico puede crear, firmar y consultar encuentros clínicos.
Los registros son inmutables después de firmados. PHI cifrado en reposo.
**Checkpoint:** requiere revisión humana al completar.
**Servicios:** `clinical-service` + `physician-portal`
**Regulación:** Res. 3100/2019, Ley 527/1999, Ley 1581/2012, HIPAA §164.312

### F4-T01 — Dominio de Clinical Records
- [x] Crear entidades: `Patient`, `Encounter`, `Prescription`, `Allergy`,
  `ActiveMedication`
- [x] Crear value objects: `DocumentId` (validación por tipo: cédula, pasaporte, NIT),
  `ICD10Code`, `BloodType`
- [x] Crear puertos: `PatientRepository`, `EncounterRepository`, `AuditLogPort`,
  `EncryptionPort`, `FhirExporter`
- [x] Regla de dominio: `Encounter` es inmutable después de `signedAt != null`
- [x] Regla de dominio: prescripción advierte (no bloquea) si hay conflicto con alergia
- [x] Crear eventos: `EncounterSigned`, `PatientRegistered`
- [x] Test unitario: intento de modificar encounter firmado lanza `ImmutableRecordException`
- [x] Test unitario: prescripción con alergia conocida genera warning en resultado

**Commit:** `feat(clinical): Add patient and encounter domain with immutability rules`

### F4-T02 — Cifrado PHI y audit log
- [x] Implementar `VaultEncryptionAdapter` — cifra/descifra con AES-256-GCM usando
  clave por tenant almacenada en Supabase Vault (o simulado en local con clave de entorno)
- [x] Aplicar cifrado en campos PHI de `Patient`: `fullName`, `phone`, `email`,
  `emergencyContact`
- [x] Aplicar cifrado en campos clínicos de `Encounter`: `chiefComplaint`,
  `physicalExam`, `treatmentPlan`, `followUpInstructions`
- [x] Implementar `PhiAuditLogAdapter` — INSERT-only en tabla `phi_audit_log`
- [x] Configurar usuario de BD `app_clinical_user` con REVOKE UPDATE, DELETE
  en `phi_audit_log`
- [x] Test: lectura de patient genera exactamente 1 entrada en `phi_audit_log`
- [x] Test: intento de DELETE en `phi_audit_log` es rechazado por la BD
- [x] Crear `docs/adr/ADR-005.md` — cifrado a nivel de aplicación vs. solo disco

**Commit:** `security(clinical): Add PHI encryption and immutable audit log`

### F4-T03 — API de pacientes y encuentros
- [x] Crear `POST /api/v1/patients` — registra paciente con PHI cifrado
- [x] Crear `GET /api/v1/patients/:id` — descifra y devuelve PHI + audit log
- [x] Crear `POST /api/v1/patients/:id/encounters` — crea encuentro clínico
- [x] Crear `POST /api/v1/patients/:id/encounters/:eid/sign` — firma y bloquea encuentro
- [x] Crear `GET /api/v1/patients/:id/export/pdf` — genera PDF con HCE completa
- [x] Crear `GET /api/v1/patients/:id/export/fhir` — genera FHIR R4 JSON
- [x] Security: RECEPTIONIST no puede leer encounters — devuelve 403
- [x] Security: PHYSICIAN solo accede a pacientes de su tenant
- [x] Test: RECEPTIONIST intentando GET /encounters devuelve 403
- [x] Test: cross-tenant patient access devuelve 403
- [x] Test: PUT en encounter firmado devuelve 409

**Commit:** `feat(clinical): Add patient and encounter endpoints with PHI protection`

### F4-T04 — UI de historia clínica en Physician Portal
- [ ] Crear página `/patients/:id` — perfil del paciente con alergias y medicamentos
- [ ] Crear página `/patients/:id/encounters/new` — formulario de nuevo encuentro
  con campos: motivo, examen físico, diagnóstico (búsqueda ICD-10), plan, receta
- [ ] Mostrar warning visual si la receta conflictúa con alergia conocida
- [ ] Botón de firma con confirmación explícita y mensaje de irreversibilidad
- [ ] i18n: todos los textos localizados incluyendo advertencias clínicas
- [ ] Test Playwright: flujo completo creación → firma → verificar que edición falla

**Commit:** `feat(physician-portal): Add clinical encounter UI with signing flow`

### F4-T05 — Resolución de conflicto GDPR vs. retención (ADR-003)
- [ ] Implementar pseudonymization store separado para pacientes EU
- [ ] Crear workflow de data subject request en `POST /api/v1/patients/:id/gdpr-request`
- [ ] Crear `docs/adr/ADR-006.md` — resolución del conflicto GDPR erasure vs.
  retención 15 años MinSalud (referencia SRS sección FR-CLN-06)
- [ ] Test: erasure request de paciente EU elimina identity store, retiene HCE pseudónima

**Commit:** `feat(clinical): Add GDPR erasure workflow with pseudonymization`

**>> CHECKPOINT FASE 4 — Esperar revisión humana antes de continuar <<**

---

## FASE 5 — Facturación y RIPS

**Objetivo:** se genera una factura automáticamente al completar una cita y
el administrador puede exportar RIPS en formato JSON validado.
**Checkpoint:** requiere revisión humana al completar.
**Servicios:** `billing-service` (consume eventos de `scheduling-service`)
**Regulación:** Resolución 2275/2023 (RIPS JSON)

### F5-T01 — Dominio de Billing
- [ ] Crear entidades: `Invoice`, `InvoiceItem`, `Payment`
- [ ] Crear value objects: `Money` (amount + ISO 4217 currency), `InvoiceStatus`
- [ ] Crear puertos: `InvoiceRepository`, `PaymentRepository`, `RipsExporter`
- [ ] Crear eventos consumidos: `AppointmentCompleted` → genera invoice
- [ ] Crear caso de uso: `GenerateInvoiceUseCase`, `ExportRipsUseCase`
- [ ] Regla de dominio: invoice solo editable en estado PENDING
- [ ] Test unitario: `GenerateInvoiceUseCase` produce invoice correcto desde evento

**Commit:** `feat(billing): Add invoice domain with event-driven generation`

### F5-T02 — Consumidor Kafka y generación automática
- [ ] Implementar Kafka consumer en `billing-service` escuchando topic `appointments`
- [ ] Al recibir `AppointmentCompleted`: invocar `GenerateInvoiceUseCase`
- [ ] Idempotency: procesar cada evento exactamente una vez (offset commit manual)
- [ ] Test de integración: publicar `AppointmentCompleted` → verificar invoice creada

**Commit:** `feat(billing): Add Kafka consumer for automatic invoice generation`

### F5-T03 — Exportador RIPS
- [ ] Implementar `RipsJsonExporter` generando estructura según Res. 2275/2023
- [ ] Validar JSON generado contra schema oficial antes de devolver al cliente
- [ ] Si validación falla: devolver errores en lenguaje de negocio, no errores de schema
- [ ] Test: RIPS generado pasa validación con datos reales de prueba
- [ ] Test: RIPS con datos inválidos devuelve errores descriptivos en español e inglés

**Commit:** `feat(billing): Add RIPS JSON exporter with schema validation`

### F5-T04 — API de facturación
- [ ] Crear `GET /api/v1/invoices` — lista con filtros y paginación
- [ ] Crear `POST /api/v1/invoices` — creación manual
- [ ] Crear `PATCH /api/v1/invoices/:id` — edición (solo PENDING)
- [ ] Crear `GET /api/v1/rips/export?from=&to=` — exporta RIPS para rango de fechas
- [ ] Security: solo TENANT_ADMIN accede a facturación
- [ ] i18n: montos con formato de moneda según locale (COP, USD, EUR)

**Commit:** `feat(billing): Add billing API endpoints with RIPS export`

**>> CHECKPOINT FASE 5 — Esperar revisión humana antes de continuar <<**

---

## FASE 6 — Notificaciones y recordatorios

**Objetivo:** los pacientes reciben recordatorios 24h y 2h antes de su cita
por email y WhatsApp. Los fallos se reintentan automáticamente.
**Checkpoint:** requiere revisión humana al completar.
**Servicios:** `notification-service` (Python FastAPI, consume eventos Kafka)

### F6-T01 — Consumidor Kafka en Notification Service
- [ ] Implementar consumer Kafka en Python consumiendo topic `appointments`
- [ ] Al recibir `AppointmentBooked`: programar recordatorio 24h y 2h antes
- [ ] Al recibir `AppointmentCancelled`: cancelar recordatorios pendientes
- [ ] Persistir `NotificationJob` en BD con estado SCHEDULED/SENT/FAILED
- [ ] Test: `AppointmentBooked` genera exactamente 2 jobs de notificación

**Commit:** `feat(notification): Add Kafka consumer for appointment events`

### F6-T02 — Envío de recordatorios
- [ ] Implementar `EmailNotifier` con plantillas localizadas (es-CO, en-US)
- [ ] Implementar `WhatsAppNotifier` via Meta Cloud API
- [ ] Scheduler que procesa jobs pendientes cada minuto
- [ ] Retry con backoff exponencial: 3 intentos (5min, 15min, 1h)
- [ ] Fallo permanente: loguear + marcar FAILED + alertar en dashboard admin
- [ ] Test: fallo de envío activa reintento con delay correcto
- [ ] Test: 3 fallos consecutivos marcan job como FAILED permanentemente

**Commit:** `feat(notification): Add email and WhatsApp reminders with retry logic`

### F6-T03 — Preferencias de notificación
- [ ] Crear `GET/PATCH /api/v1/notification-preferences` por paciente
- [ ] Respetar opt-out en el scheduler (no enviar si opted_out = true)
- [ ] Registrar consentimiento en `consent_log` (GDPR Art. 7)
- [ ] Test: paciente con opt-out no recibe notificación

**Commit:** `feat(notification): Add notification preferences with GDPR consent log`

**>> CHECKPOINT FASE 6 — Esperar revisión humana antes de continuar <<**

---

## FASE 7 — Portal del paciente

**Objetivo:** el paciente puede ver sus citas, descargar su HCE y gestionar
sus preferencias sin llamar a la clínica.
**Checkpoint:** requiere revisión humana al completar.
**Servicios:** `patient-portal` (Next.js) + APIs existentes

### F7-T01 — Autenticación del paciente
- [ ] Crear auth separada para pacientes en `patient-portal` (distinta de physician)
- [ ] Login con email + password o Google OAuth
- [ ] MFA opcional con prompt de activación en primer login
- [ ] Middleware Next.js: redirige a login si no hay sesión válida
- [ ] Test Playwright: login → redirección correcta → logout

**Commit:** `feat(patient-portal): Add patient authentication flow`

### F7-T02 — Vistas del portal del paciente
- [ ] Crear página `/appointments` — próximas y pasadas con estado
- [ ] Crear página `/records` — resumen de HCE: diagnósticos, alergias, medicamentos
- [ ] Crear botones de descarga: HCE en PDF y FHIR JSON
- [ ] Crear página `/preferences` — gestión de notificaciones con opt-in/opt-out
- [ ] Crear página `/data-request` — solicitud GDPR/habeas data con formulario
- [ ] WCAG 2.1 AA: ejecutar axe-core en cada página con Playwright
- [ ] i18n: todos los textos en es-CO y en-US
- [ ] Test Playwright: flujo descarga HCE PDF y verificación del archivo

**Commit:** `feat(patient-portal): Add patient self-service pages with accessibility`

**>> CHECKPOINT FASE 7 — Esperar revisión humana antes de continuar <<**

---

## FASE 8 — Reportes y auditoría

**Objetivo:** el administrador tiene visibilidad operacional y financiera
y puede exportar logs de auditoría PHI para cumplimiento regulatorio.
**Checkpoint:** requiere revisión humana al completar.
**Servicios:** `billing-service` + `clinical-service` + `physician-portal`

### F8-T01 — Reportes operacionales
- [ ] Crear `GET /api/v1/reports/operational` — volumen de citas, tasa de no-show,
  duración promedio, por médico y período
- [ ] Crear `GET /api/v1/reports/revenue` — ingresos por período, médico, tipo
- [ ] Implementar con CQRS: queries de lectura separadas de las escrituras
  (repositorios de solo lectura con projections optimizadas)
- [ ] Test: reporte con rango de fecha vacío devuelve estructura correcta con ceros

**Commit:** `feat(reporting): Add operational and revenue reports with CQRS reads`

### F8-T02 — Dashboard de reportes en Physician Portal
- [ ] Crear página `/reports` con filtros de fecha y médico
- [ ] Gráficas: volumen de citas por semana (Chart.js o Recharts)
- [ ] Tabla de ingresos por período exportable a CSV
- [ ] i18n: formatos de fecha y moneda según locale

**Commit:** `feat(physician-portal): Add reports dashboard with charts`

### F8-T03 — Auditoría PHI exportable
- [ ] Crear `GET /api/v1/audit/phi` — export paginado del log de acceso PHI
- [ ] Solo accesible por TENANT_ADMIN
- [ ] Export como CSV para uso en auditorías regulatorias
- [ ] Test: PHYSICIAN intentando acceder a audit log devuelve 403

**Commit:** `feat(reporting): Add PHI audit log export for compliance`

**>> CHECKPOINT FASE 8 — Esperar revisión humana antes de continuar <<**

---

## FASE 9 — Observabilidad e infraestructura

**Objetivo:** el sistema es observable en producción, la infraestructura
está como código y el pipeline CI/CD es completo.
**Checkpoint:** requiere revisión humana al completar.

### F9-T01 — Observabilidad
- [ ] Instrumentar todos los servicios Java con OpenTelemetry (traces + métricas)
- [ ] Instrumentar Notification Service Python con OpenTelemetry
- [ ] Configurar Prometheus scraping en todos los servicios
- [ ] Crear dashboards Grafana para: latencia API, tasa de errores,
  booking conflicts, notificaciones fallidas
- [ ] Configurar alertas: auth spike, conflict rate, notification failures
- [ ] Verificar: métricas del SRS sección 14.2 aparecen en Grafana

**Commit:** `feat(observability): Add OpenTelemetry, Prometheus and Grafana dashboards`

### F9-T02 — Infraestructura e IaC
- [ ] Crear módulos / scripts de IaC para los proveedores escogidos:
  - Supabase: provisión de proyecto, bases de datos (schema-per-tenant) y Vault
  - Confluent Cloud: proyectos, topics y credenciales
  - Upstash: configuración de Redis serverless (documentar secretos y endpoints)
  - Railway: despliegue de servicios (documentar variables de entorno y despliegue vía Nixpacks)
  - Storage: provisión de almacenamiento compatible (ej. Supabase Storage) si aplica
- [ ] Crear módulo para provisión de tenant (secret Vault por tenant en Supabase)
- [ ] Crear workspaces/entornos: `staging` y `production` (IaC donde aplique)
- [ ] Checkov / herramientas de análisis de IaC pasan sin findings HIGH/CRITICAL
- [ ] Documentar en `docs/INFRASTRUCTURE.md` el diagrama de red y la topología de servicios

**Commit:** `infra: Add IaC modules for Supabase, Confluent Cloud, Upstash and Railway`

### F9-T03 — Pipeline CI/CD completo
- [ ] Completar `.github/workflows/ci.yml` con todos los checks definidos en F0-T06
- [ ] Agregar `.github/workflows/deploy.yml`: staging automático en merge a main,
  producción con aprobación manual
- [ ] Agregar Playwright E2E en pipeline contra staging
- [ ] Agregar k6 load test en pipeline: slot query < 300ms p95, booking < 500ms p95
- [ ] Verificar: pipeline completo pasa en verde antes de checkpoint

**Commit:** `ci: Complete CI/CD pipeline with E2E, load tests and deployment`

### F9-T04 — Documentación final
- [ ] Completar `docs/API.md` con todos los endpoints de todas las fases
- [ ] Completar `docs/DATA_MODEL.md` con diagrama final
- [ ] Completar `SECURITY.md` con evidencia de tests para cada ítem OWASP 2025
- [ ] Completar `THREAT_MODEL.md` con todas las amenazas identificadas durante el desarrollo
- [ ] Crear `docs/SETUP.md` — guía paso a paso para levantar el proyecto localmente
- [ ] Crear `docs/DEPLOYMENT.md` — guía de despliegue con Railway, Supabase, Confluent Cloud y Terraform/providers donde aplique
- [ ] Crear `docs/AUTONOMY_GUIDE.md` — guía en lenguaje no técnico para que el
  administrador de la clínica opere el sistema (valor ElevaForge)

**Commit:** `docs: Complete project documentation for all phases`

**>> CHECKPOINT FASE 9 — Revisión final del proyecto completo <<**

---

## Decisiones arquitectónicas registradas

| ADR | Título | Estado | Fase |
|-----|--------|--------|------|
| ADR-001 | Schema-per-tenant vs row-level tenancy | Pendiente | F1-T07 |
| ADR-002 | RS256 vs HS256 para JWT | Pendiente | F1-T07 |
| ADR-003 | Argon2id para hashing de contraseñas | Pendiente | F1-T07 |
| ADR-004 | Optimistic locking vs pessimistic locking | Pendiente | F3-T02 |
| ADR-005 | Cifrado PHI a nivel de aplicación vs. solo disco | Aceptado | F4-T02 |
| ADR-006 | GDPR erasure vs. retención 15 años MinSalud | Pendiente | F4-T05 |
| ADR-007 | Sin Docker: Zonky + EmbeddedKafka + Railway + Supabase | Pendiente | F0-T02 |

Toda decisión nueva no prevista aquí debe generar un ADR antes de implementarse.

---

## Métricas de calidad objetivo

| Métrica | Target | Fase de verificación |
|---------|--------|---------------------|
| Cobertura domain/ | 100% | Cada fase |
| Cobertura application/ | 80% | Cada fase |
| Semgrep findings HIGH/CRITICAL | 0 | CI en cada PR |
| SCA (mvn dependency-check / pip-audit) CRITICAL | 0 | CI en cada PR |
| Slot query p95 | < 300ms | F9-T03 |
| Booking p95 | < 500ms | F9-T03 |
| Lighthouse Patient Portal | ≥ 90 | F7-T02 |
| WCAG 2.1 AA violations | 0 | F7-T02 |
| OWASP ZAP HIGH/CRITICAL | 0 | F9-T03 |
