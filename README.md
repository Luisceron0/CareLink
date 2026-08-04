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

| Módulo | Estado |
|---|---|
| Identity — tenants, usuarios, auth RS256/JWKS, Argon2id, rotación de refresh tokens | Construido, parcial |
| Contención (`DemoModeGuard`) + audit log append-only | Sub-fase 1 |
| Paciente + encuentro clínico firmado e inmutable | Sub-fase 2 |
| Admisiones + triage Manchester | Sub-fase 3 |
| Diario de enfermería + motor de conocimiento | Sub-fase 4 |
| Interconsultas con revocación de acceso por request | Sub-fase 5 |
| Laboratorio + farmacia | Sub-fase 6 |
| Frontend (SPA React + Vite, vistas por rol) | Sub-fase 7 |

El plan por sub-fases está en [tasks/todo.md](tasks/todo.md). Cada una termina en algo
que corre: si el trabajo se corta a mitad de camino queda una demo funcional de lo
construido, no ocho módulos a medias.

## Arranque local

Requiere Docker y JDK 21.

```bash
cp .env.example .env       # completar REFRESH_TOKEN_HMAC_SECRET y POSTGRES_PASSWORD
docker compose up
```

Levanta backend (`:8080`), PostgreSQL 16 (`:5432`) y frontend (`:5173`, placeholder
hasta la Sub-fase 7). Health del backend: `http://localhost:8080/actuator/health`.

Tests:

```bash
./mvnw -f services/identity-service/pom.xml test
```

## Estructura

- `services/identity-service/` — el backend. Contiene **ambos** bounded contexts:
  `identity/` (tenants, usuarios, sesiones) y, a partir de la Sub-fase 2, `clinical/`
  (PHI). Son dos contextos, no ocho microservicios — ver [SRS §3.3](docs/SRS.md).
- `docs/SRS.md` — fuente de verdad única. Un solo archivo, sin espejos.
- `docs/adr/` — decisiones arquitectónicas, incluidas las superadas.
- `docs/archive/` — los SRS de origen (CareLink v1.0, plan v1.0), conservados como
  insumo histórico.
- `migrations/` — SQL versionado.
- `tasks/` — plan activo (`todo.md`) y lecciones aprendidas (`lessons.md`).

## Seguridad

El modelo de amenazas, los controles y los criterios de aceptación verificables están en
[SRS §8](docs/SRS.md) y §18. Los que gobiernan el diseño:

- Aislamiento por schema-per-tenant, más filtro por `service_id` dentro del tenant.
- El acceso de un especialista vía interconsulta **se revalida en cada request** — nunca
  se cachea el resultado.
- El motor de conocimiento suprime resultados con `COUNT(DISTINCT patient_id) < 5`,
  en la query y no en la interfaz.
- `audit_log` es append-only a nivel de base de datos; el usuario de aplicación no tiene
  grant de DELETE.
- Cifrado de PHI AES-256-GCM con IV aleatorio por operación y clave por tenant.

Reporte de vulnerabilidades: [SECURITY.md](SECURITY.md).

## Licencia

Sin definir — ADR-011 pendiente.
