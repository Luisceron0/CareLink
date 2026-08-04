# Instrucciones para el agente de código — CareLink (unificado v3.0)

## Contexto del proyecto

CareLink es una **implementación de referencia** (no producto) de una plataforma clínica
multi-tenant. Desde el SRS v3.0, integra el dominio clínico de ClinicTrack ESE (historia
clínica rica: triage, diario de enfermería NANDA/NIC/NOC, motor de conocimiento con
k-anonimato, interconsultas, labs, farmacia) sobre la arquitectura multi-tenant de
CareLink. Un ESE (hospital público) es simplemente **un tenant más** — no hay un segundo
modelo de datos ni un segundo sistema.

Regla que gobierna todo: nunca PHI real, nunca producción, contención mecánica no
opcional. Ver `docs/SRS.md` §1.6 antes de escribir código.

Fuente de verdad única: `docs/SRS.md` v3.0. Este archivo se actualiza si el SRS cambia,
nunca al revés.

## Stack tecnológico

- Backend: Java 21, Spring Boot 3.3.x, Spring Security, Spring Data JPA, Flyway
- DB: PostgreSQL 16 — schema-per-tenant
- Frontend: React 18 + Vite + Tailwind — **una sola SPA**, vistas por rol. No Next.js,
  no portales separados (ADR-014)
- Local: Docker Compose — backend + Postgres + frontend en un comando (ADR-012)
- Test: JUnit 5 + Zonky embedded PostgreSQL
- SAST: Semgrep (reglas extendidas a concatenación JPQL, no solo SQL nativo) · Secrets:
  Gitleaks · SCA: `mvn dependency-check`

No hay entorno de producción ni demo público (ADR-015). No se agrega Kafka, Redis, ni un
segundo runtime backend sin aprobación explícita — no lo requiere nada del SRS §5.

## Principios de código

1. **Dos bounded contexts, no ocho microservicios.** Identity y Clinical Records. Los
   ocho módulos de ClinicTrack (Patient, Encounter, Triage, Diary, Knowledge Engine,
   Interconsultations, Labs, Pharmacy) son sub-dominios **dentro** de Clinical Records,
   comparten schema por tenant y un solo mecanismo de auditoría. No se separan en
   servicios independientes — eso sería repetir el error de sobre-fragmentación que ya
   se corrigió una vez en este proyecto.
2. **Hexagonal, siempre.** `domain` no importa `infrastructure`. Casos de uso dependen de
   ports.
3. **Value objects para invariantes.** `TenantSlug`, no `String` (lección de ADR-010).
4. **Auditoría vía AOP, no por disciplina.** `@Auditable` intercepta cada método de
   servicio con operación crítica sobre PHI. Un endpoint nuevo que "se olvida" de auditar
   no debería ser posible — si tu cambio requiere invocar el logging manualmente en cada
   controller, estás yendo contra el patrón.
5. **Vertical slices, no capas horizontales.** El Milestone 1 se construye por sub-fase
   (ver `tasks/todo.md`), y cada sub-fase termina en algo que corre, no en "toda la capa
   de dominio de los 8 módulos sin ningún endpoint funcionando".

## Patrones de arquitectura obligatorios

```
services/identity-service/src/main/java/com/carelink/
├── identity/
│   ├── domain/  application/  infrastructure/
└── clinical/
    ├── domain/          # Patient, ClinicalEncounter, HealthDiaryEntry, etc.
    ├── application/      # casos de uso por sub-módulo
    └── infrastructure/   # adapters JPA, controllers, EncryptionService
```

## Seguridad — reglas no negociables (SRS §8)

- **Cabeceras HTTP son input no confiable.** Nunca concatenadas en SQL/JPQL/comandos.
  Todo write a DB —incluida auditoría— usa parametrización. Esto aplica también a las
  queries del Motor de Conocimiento (JPQL con filtros de usuario, §5.6/§8.4).
- **Identificadores dinámicos se validan en el sink** (`TenantSlug`, no `String` crudo).
- **Acceso de especialista se revalida en cada request**, nunca se cachea el resultado de
  "tiene interconsulta activa" — FR-CLN-10 / AC-13. Esto es memoria de un patrón de
  seguridad, no un detalle de implementación: la caché de autorización temporal es
  exactamente donde este tipo de sistema falla en producción real.
- **k-anonimato es server-side y no opcional.** El Motor de Conocimiento nunca devuelve
  un resultado con `COUNT(DISTINCT patient_id) < K_ANONYMITY_THRESHOLD` — FR-CLN-07 /
  AC-14. No se "arregla" en el frontend ocultando filas; se bloquea en la query.
- **`audit_log` es append-only a nivel de DB**, no solo de aplicación. El usuario de
  aplicación no tiene grant de DELETE — verificalo, no lo asumas (AC-10).
- **`service_id` filtra acceso dentro de un tenant** con el mismo rigor que el tenant
  filtra entre organizaciones — cobertura de test 100% en ese path específico (heredado
  del estándar de ClinicTrack para ese riesgo).
- **`DemoModeGuard` no se toca sin discutirlo primero.**
- **Nunca dos implementaciones del mismo bounded context** (ya pasó una vez — ADR-010).
- **Cifrado:** AES-256-GCM, IV aleatorio por operación (`SecureRandom.getInstanceStrong()`),
  clave por tenant. Nunca texto plano en columnas PHI.

## Supuestos prohibidos — nunca asumas esto sin confirmación explícita

- No asumas que un módulo clínico "menor" (ej. Farmacia) puede compartir código con otro
  sin pasar por su propio caso de uso — la tentación de generalizar antes de tiempo es
  exactamente lo que el SRS pide evitar (YAGNI, §6).
- No asumas que el acceso de un especialista sigue vigente porque "recién se validó hace
  un momento" — se revalida siempre, sin excepción de performance.
- No asumas que un resultado del Motor de Conocimiento con pocos registros es "igual de
  seguro mostrarlo, son datos sintéticos" — el umbral de k-anonimato se respeta también
  en el demo, porque es la funcionalidad que se está demostrando, no un obstáculo a saltear.
- No asumas alcance ampliado a Scheduling/Billing/Notifications porque "ya que estamos" —
  siguen fuera de este milestone (§16.3 del SRS).
- No asumas que agregar Redis o Kafka resuelve un problema de performance sin antes medir
  — nada en el SRS los requiere todavía.

## Gestión de tareas

Al inicio de cada sesión: `docs/SRS.md` (§16.2 sub-fase activa, §18 AC relevantes) →
`tasks/todo.md` → `tasks/lessons.md`.
Al cierre: actualizar `todo.md` (estado) y `lessons.md` si hubo corrección o decisión no
trivial.

## Workflow de desarrollo

- TDD para dominio y casos de uso.
- Una tarea de `todo.md` = un PR. Una sub-fase = varios PRs, pero no se salta a la
  siguiente sub-fase sin que la actual esté demostrable.
- Ninguna tarea se marca `[x]` sin su AC del SRS §18 evidenciado — para las sub-fases 2 a
  6 eso incluye el test de seguridad específico de esa sub-fase (revocación de
  interconsulta, k-anonimato, grant de DELETE, etc.), no solo "los tests pasan".

## Comandos del entorno

- Local: `docker compose up`
- Tests: `./mvnw test` (unit) · `./mvnw verify` (integración, Zonky)
- Frontend: `npm run dev` (Vite) — desde Sub-fase 7
- SAST: `semgrep --config .semgrep/ services/identity-service`
- Secrets: `gitleaks detect --source . -r`

## Límites y claridad

- No se crean servicios nuevos (`scheduling-service`, `billing-service`, etc.) — siguen
  fuera de scope (§16.3).
- No se despliega ningún entorno público sin decisión explícita nueva (ADR-015 lo cierra
  por ahora).
- Cambio de sub-fase fuera de orden, de stack, o reversión de un ADR: confirmación
  explícita del usuario antes de proceder.