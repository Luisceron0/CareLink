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

