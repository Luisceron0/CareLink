# CareLink

Implementación de referencia de una plataforma clínica multi-tenant, con el dominio
clínico de un hospital público colombiano (ESE) construido sobre ella: historia clínica,
triage, diario de enfermería NANDA/NIC/NOC, motor de conocimiento con k-anonimato,
interconsultas, laboratorio y farmacia.

> **No es un producto.** No maneja información de salud de personas reales, no soporta
> operación clínica y no tiene entorno de producción — por diseño, no por falta de
> tiempo. Ver [SRS §1.6](docs/SRS.md) para la versión verificable de esta afirmación:
> el arranque falla fuera de modo demo y contra una base sin el sello de datos
> sintéticos.

## Qué hay construido hoy

**Las nueve sub-fases del Milestone 1 están cerradas.** Cada una con su criterio de
aceptación verificado por test de integración *y* contra el stack real levantado — no
solo tests en verde (ver por qué eso importa en [tasks/lessons.md](tasks/lessons.md)).

| Módulo | Estado |
|---|---|
| Contención (`DemoModeGuard`) + audit log append-only, PostgreSQL con dos roles | Sub-fase 1 |
| Identity: tenants, invitación de usuarios por rol, auth RS256/JWKS, Argon2id | Sub-fase 2 |
| Paciente + encuentro clínico firmado e inmutable a nivel de trigger de base | Sub-fase 2 |
| Admisiones + triage Manchester | Sub-fase 3 |
| Diario de enfermería (NANDA/NIC/NOC) + motor de conocimiento con k-anonimato | Sub-fase 4 |
| Interconsultas, con acceso del especialista revalidado en cada request | Sub-fase 5 |
| Laboratorio + farmacia (adherencia, conflictos que advierten sin bloquear) | Sub-fase 6 |
| Frontend: SPA React 18 + Vite, vistas por rol, verificada en navegador real | Sub-fase 7 |
| Auditoría de seguridad end-to-end: sqlmap, SAST propio, 1 hallazgo alto corregido | Sub-fase 8 |

El plan por sub-fases está en [tasks/todo.md](tasks/todo.md), con la evidencia de cada
criterio de aceptación. Cada sub-fase termina en algo que corre: si el trabajo se
hubiera cortado a mitad de camino, quedaba una demo funcional de lo construido hasta
ahí, no ocho módulos a medias.

## Arranque local

Requiere Docker.

```bash
cp .env.example .env       # completar REFRESH_TOKEN_HMAC_SECRET, POSTGRES_PASSWORD,
                            # CARELINK_APP_DB_PASSWORD y CLINIC_ENCRYPTION_KEY
                            # (openssl rand -base64 32 para las dos últimas)
docker compose up
```

Levanta backend (`:8080`), PostgreSQL 16 (`:5432`), frontend (`:5173`) y Mailpit
(`:8025`, captura los correos de verificación/invitación en vez de enviarlos de
verdad). Health del backend: `http://localhost:8080/actuator/health`.

Los cuatro servicios exponen `healthcheck` — `docker compose ps` los muestra `healthy`
cuando el stack está realmente arriba, no solo con el proceso corriendo.

Tests (requiere JDK 21 — `docker compose` no lo necesita en el host, la imagen lo trae):

```bash
export JAVA_HOME=/path/a/tu/jdk-21
./mvnw -f services/identity-service/pom.xml verify   # unit + integration, PostgreSQL real embebido
```

## Estructura

- `services/identity-service/` — el backend. Contiene **ambos** bounded contexts:
  `identity/` (tenants, usuarios, sesiones) y `clinical/` (PHI: pacientes, encuentros,
  diario, laboratorio, farmacia, interconsultas). Son dos contextos dentro de un mismo
  servicio, no ocho microservicios — ver [SRS §3.3](docs/SRS.md).
- `frontend/` — SPA React 18 + Vite + Tailwind, vistas por rol (ADR-014).
- `docs/SRS.md` — fuente de verdad única del proyecto. Un solo archivo, sin espejos:
  requisitos, modelo de amenazas (§8), ADRs (§17) y criterios de aceptación con su
  estado real (§18) viven ahí, no repartidos en documentos que puedan divergir.
- `docs/adr/` — decisiones arquitectónicas standalone, incluidas las superadas.
- `docs/security/` — reportes de auditoría, con hallazgos, severidad y evidencia.
- `docs/archive/` — los SRS de origen (CareLink v1.0, plan v1.0), conservados como
  insumo histórico.
- `services/identity-service/src/main/resources/db/migration/` — migraciones Flyway.
- `tasks/` — plan activo (`todo.md`, una tarea = un commit) y lecciones aprendidas
  (`lessons.md`) — los defectos reales encontrados durante la construcción, con su causa
  raíz, no solo la corrección.

## Seguridad

El modelo de amenazas, los controles y los criterios de aceptación verificables están en
[SRS §8](docs/SRS.md) y §18. Los que gobiernan el diseño:

- Aislamiento por schema-per-tenant, más filtro por `service_id` dentro del tenant —
  en el `WHERE`/`HAVING` de la consulta, no sobre filas ya traídas.
- El acceso de un especialista vía interconsulta **se revalida en cada request** — nunca
  se cachea el resultado; verificado por HTTP con el mismo JWT pasando de 200 a 403 al
  cerrarse la interconsulta, sin re-login.
- El motor de conocimiento suprime resultados con `COUNT(DISTINCT patient_id) < 5`,
  dentro de la query — las filas por debajo del umbral nunca salen de la base.
- `audit_log` y la historia clínica firmada son inmutables por trigger de PostgreSQL,
  para cualquier rol que conecte, incluido el administrador — no solo lógica de
  aplicación.
- Cifrado de PHI AES-256-GCM con IV aleatorio por operación y clave derivada por tenant.
- La aplicación **se niega a arrancar** sin sus secretos configurados — ver el hallazgo
  real donde esto falló y cómo se corrigió en la auditoría de Sub-fase 8.

**Auditoría completa (Sub-fase 8):** SAST propio (reglas semgrep verificadas contra
código deliberadamente vulnerable antes de confiar en ellas), `sqlmap --level 3` contra
la instancia real, barrido de credenciales, y un hallazgo de severidad alta encontrado y
corregido con evidencia de que el gate que debía prevenirlo no lo detectaba. Reporte
completo: [docs/security/AUDIT-2026-08-06.md](docs/security/AUDIT-2026-08-06.md).

Ver [SECURITY.md](SECURITY.md) para el mapa completo de dónde vive cada cosa.

## Licencia

MIT — ver [LICENSE](LICENSE).
