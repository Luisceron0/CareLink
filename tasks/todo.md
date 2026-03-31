# TODO — CareLink

## Sesión actual

- [x] Crear archivo de instrucciones de Copilot en `.github/copilot-instructions.md`
- [x] Crear carpeta `tasks/` con archivos persistentes
- [x] Crear `tasks/lessons.md`
- [ ] Verificar estructura documental base (`docs/SRS.md`, `docs/API.md`, `docs/DATA_MODEL.md`, `docs/adr/`)
- [ ] Definir siguiente tarea de desarrollo (MVP vertical slice)

### Preparación para commit/infra
- [x] Crear ADR-008 — Infra: Railway / Supabase / Upstash / Confluent (`docs/adr/ADR-008.md`)
- [x] Añadir script `scripts/prepare_commit.sh` para preparar commit
- [x] Actualizar `tasks/todo.md` con pasos de commit y ADR
- [ ] Ejecutar tests y linters localmente antes de push
- [ ] Ejecutar `scripts/prepare_commit.sh` y hacer `git push` (requiere credenciales)

## Revisión

Pendiente al cierre de la sesión.

## FASE 3 — Reserva de cita (avance actual)

- [x] F3-T01 — Dominio base de Appointment
- [x] F3-T02 — Entidad JPA `Appointment` con `@Version`
- [x] F3-T02 — Migración SQL con `version` + índice único parcial por slot activo
- [x] F3-T02 — Manejo de conflicto en alta concurrencia (`DataIntegrityViolationException` / optimistic locking)
- [x] F3-T02 — Test de concurrencia 10 threads (1 éxito, 9 conflicto)
- [x] F3-T03 — Endpoints `POST/GET/PATCH/DELETE /api/v1/appointments`
- [x] F3-T03 — Publicador de eventos Kafka (`appointments`)
- [x] F3-T04 — UI base `/appointments/new` y `/appointments` (polling 30s)
- [x] F3-T04 — Modal de conflicto con 3 alternativas en `/appointments/new`
- [ ] F3 — Ejecutar verificación local `checkstyle`, `test`, `npm lint`, `npm type-check`
- [ ] F3-T02 — ADR-004 (optimistic vs pessimistic locking)

## FASE 4 — Historia clínica (avance actual)

- [x] F4-T01 — Entidades de dominio (`Patient`, `Encounter`, `Prescription`, `Allergy`, `ActiveMedication`)
- [x] F4-T01 — Value objects (`DocumentId`, `ICD10Code`, `BloodType`)
- [x] F4-T01 — Puertos (`PatientRepository`, `EncounterRepository`, `AuditLogPort`, `EncryptionPort`, `FhirExporter`)
- [x] F4-T01 — Eventos (`EncounterSigned`, `PatientRegistered`)
- [x] F4-T01 — Casos de uso base (`RegisterPatientUseCase`, `SignEncounterUseCase`, `EvaluatePrescriptionUseCase`)
- [x] F4-T01 — Tests unitarios base de inmutabilidad y advertencias de alergia
- [x] F4-T01 — Ejecutar validación local de `clinical-service` (`checkstyle` + `test`)
- [x] F4-T02 — Implementar `VaultEncryptionAdapter` (AES-256-GCM por tenant)
- [x] F4-T02 — Aplicar cifrado PHI en `Patient` y `Encounter` desde casos de uso
- [x] F4-T02 — Implementar `PhiAuditLogAdapter` insert-only
- [x] F4-T02 — Configurar `phi_audit_log` + REVOKE UPDATE/DELETE en migración tenant
- [x] F4-T02 — Test: lectura de paciente genera una entrada de auditoría
- [x] F4-T02 — Test: DELETE en `phi_audit_log` rechazado por permisos de BD
- [x] F4-T02 — Crear ADR-005 (cifrado aplicación vs disco)
- [x] F4-T02 — Ejecutar validación local de `clinical-service` (`checkstyle` + `test`)
- [x] F4-T03 — Implementar endpoints de pacientes y encuentros en `clinical-service`
- [x] F4-T03 — Implementar exportaciones `PDF` y `FHIR` con auditoría
- [x] F4-T03 — Añadir tests de integración de seguridad (`403`/`409`)
- [x] F4-T03 — Ejecutar validación local de `clinical-service` (`checkstyle` + `test`)
- [x] F4-T04 — Crear wrapper API clinical en physician-portal
- [x] F4-T04 — Implementar `/patients/:id` con perfil, alergias y medicación activa
- [x] F4-T04 — Implementar `/patients/:id/encounters/new` con diagnóstico ICD-10
- [x] F4-T04 — Mostrar warning de alergias + flujo de firma irreversible
- [x] F4-T04 — Internacionalizar UI clínica (es-CO / en-US)
- [x] F4-T04 — Añadir test Playwright del flujo crear → firmar → edición falla
- [x] F4-T04 — Ejecutar validación local de physician-portal (`lint` + `type-check`)
- [x] F4-T05 — Diseñar pseudonymization store para pacientes EU
- [x] F4-T05 — Implementar `POST /api/v1/patients/:id/gdpr-request` en clinical-service
- [x] F4-T05 — Aplicar reglas FR-CLN-06 (EU: seudonimizar/eliminar identidad, CO: retención)
- [x] F4-T05 — Crear ADR-006 (GDPR erasure vs retención 15 años)
- [x] F4-T05 — Actualizar docs (`API.md`, `THREAT_MODEL.md`, `SECURITY.md`)
- [x] F4-T05 — Añadir tests de flujo GDPR por región y validaciones de acceso
- [ ] F4-T05 — Ejecutar validación local de clinical-service (`checkstyle` + `test`)

## Implementar servicio `api-gateway-identity` (MVP mínimo)

- [x] Planificar pasos en la lista de tareas del agente
- [x] Añadir scaffold FastAPI y modelos Pydantic
- [x] Implementar POST `/api/v1/auth/register` (no persistente)
- [x] Añadir tests (`tests/test_register.py`) y ejecutar
- [x] Actualizar `docs/API.md` con el endpoint
- [ ] Ejecutar linters y revisar resultados

## Próximos pasos implementados

- [ ] Añadir herramientas dev y Makefile para el servicio `api-gateway-identity`
- [ ] Implementar `POST /api/v1/tenants/register` (mínimo)
- [ ] Añadir tests para tenant register y ejecutar
- [ ] Ejecutar linter (`ruff`) y typecheck (`mypy`) y corregir warnings

## Identity service — JWT & Vault work (actualizado)

- [x] Añadir `JwtKeyProvider` en `domain/port`
- [x] Implementar `StaticKeyProvider` (dev)
- [x] Implementar `JwksKeyProvider` (JWKS fetch + cache + TTL)
- [x] Refactorizar `JwtService` para `kid` y uso de `JwtKeyProvider`
- [x] Implementar refresh-token rotation + hashed storage
- [x] Implementar `VaultKeyProvider` y wiring por profile (prod)
- [x] Añadir tests unitarios e integración para JWKS y VaultKeyProvider
- [x] Actualizar `.env.example` con variables Vault/JWKS
- [x] Actualizar `docs/THREAT_MODEL.md` y crear ADR `ADR-00X-jwt-management.md`
 - [x] Actualizar `.env.example` con variables Vault/JWKS
 - [x] Actualizar `docs/THREAT_MODEL.md` y crear ADR `ADR-00X-jwt-management.md`
 - [x] Configurar CI job para `identity-service` (sin Docker)

## FASE 2 — Disponibilidad médica (plan en curso)

- [ ] F2-T01 — Dominio de Scheduling: crear entidades `Physician`, `AvailabilityBlock`, `BlockedPeriod`, `TimeSlot` y value objects (`SlotDuration`, `WorkingHours`, `DateRange`) en `services/scheduling-service/src/main/java` — en progreso
- [ ] F2-T02 — Infraestructura y API: `JpaPhysicianRepository`, `JpaAvailabilityRepository`, endpoints para `physicians` y `slots` — pendiente
- [ ] F2-T03 — UI: páginas del portal para agenda semanal y configuración — pendiente


## FASE 0 — Fundación (ejecutada)

Fase 0 completada (archivos y scaffolds iniciales creados). Pendientes de verificación automática:

- [x] F0-T01 — Estructura del monorepo
- [x] F0-T02 — Configuración de servicios Java
- [x] F0-T03 — Configuración de Notification Service (Python)
- [x] F0-T04 — Configuración de portales Next.js
- [x] F0-T05 — Esquema de base de datos inicial
- [x] F0-T06 — Pipeline CI/CD base

Notas:
- Se generaron scaffolds y archivos iniciales para cada tarea. Quedan por ejecutar pasos de verificación (tests, linters y builds) en los servicios individuales.

Siguiente: esperar checkpoint de FASE 0 según `tasks/PROJECT_PLAN.md` (esta fase no requiere revisión humana por política del plan).
### Revisión final

Se implementó un servicio mínimo con validación Pydantic y tests básicos. En producción falta:

- Persistencia por tenant y cifrado de PHI
- Emisión de audit logs para accesos/creaciones de PHI
- Validaciones adicionales de negocio y rate limiting

