CareLink — Monorepo

Resumen rápido

CareLink es una plataforma SaaS clínica para consultorios médicos independientes.
Este repositorio contiene múltiples servicios (Identity, Scheduling, Clinical, Billing,
Notification) y portales web (Physician, Patient).

Estructura básica

- `services/` — microservicios backend (Java + Spring Boot, Python)
- `portals/` — aplicaciones Next.js (physician-portal, patient-portal)
- `docs/` — SRS, API, DATA_MODEL, ADRs
- `migrations/` — scripts SQL de inicialización
- `tasks/` — notas, plan de proyecto, lecciones aprendidas

Quick start (desarrollo local)

1. Revisa `docs/SRS.md` y `tasks/PROJECT_PLAN.md`.
2. Carga variables de entorno desde `.env` (usa `.env.example` como plantilla).
3. Para servicios individuales sigue los README dentro de `services/*`.

Referencia

- SRS completo: `carelink-srs.md`
- Instrucciones de Copilot (agente): `.github/copilot-instructions.md`

