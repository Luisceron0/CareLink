# ADR-013 — Fusión de CareLink y ClinicTrack ESE en un solo sistema

**Fecha:** 2026-08-03
**Estado:** Aceptado con disidencia técnica registrada

## Contexto
El autor propuso fusionar CareLink (SaaS multi-tenant, SRS v2.0, Identity construido al
70%) y ClinicTrack ESE (sistema institucional single-org para hospitales públicos
colombianos, SRS v1.2.0, cero código) en un único sistema, con la instrucción explícita
de que sea "literalmente un solo sistema con ambos casos de uso" y que exista como un
único repositorio pulido, no dos.

## Disidencia técnica de Arch-Sentinel
La recomendación original fue **no fusionar los SRS**, sino mantener CareLink como
sustrato arquitectónico (multi-tenant, Identity ya construido) e importar el dominio
clínico de ClinicTrack como implementación del módulo Clinical Records, con el ESE
modelado como tenant de referencia. Razón: los dos SRS describen personas de producto
distintas (SaaS comercial autoregistrable vs. sistema institucional provisionado por TI)
y fusionarlos "literalmente" reabre el problema que motivó ADR-009 — scope prometido
mayor al scope verificable. El autor mantuvo la decisión de fusión total.

## Decisión
Se fusiona en un solo sistema. Los siguientes puntos de conflicto se resuelven así,
salvo que el autor indique lo contrario en la próxima sesión de diseño:

| Punto de conflicto | Resolución |
|---|---|
| Modelo de tenancy | Multi-tenant schema-per-tenant (CareLink). El ESE se modela como un tenant; "Servicio" (departamento) es sub-entidad dentro del tenant. |
| Identity/Auth | Arquitectura de CareLink (RS256 + JWKS + Vault + refresh rotation) — ya construida, más madura que la solución de ClinicTrack (que fue simplificada *por restricción de infra de Railway*, no por preferencia de diseño — ver ADR-002 de ClinicTrack). |
| Dominio clínico | El de ClinicTrack (más rico y mejor especificado): encuentro, diagnóstico CIE-10, diario de enfermería NANDA/NIC/NOC, motor de conocimiento con k-anonimato, interconsultas, labs, farmacia. Reemplaza el "Clinical mínimo" de CareLink §16.2. |
| Frontend | [PENDIENTE — Next.js de CareLink vs. React+Vite de ClinicTrack, ver pregunta abierta] |
| Scope de Milestone 1 | [PENDIENTE — al incorporar el dominio de ClinicTrack, Milestone 1 deja de ser "mínimo"; requiere re-fasear] |
| Deployment | [PENDIENTE — Railway 512MB (restricción real de ClinicTrack) vs. sin restricción declarada de CareLink] |

## Consecuencias
- El scope de Milestone 1 crece sustancialmente respecto de lo acordado en ADR-009.
  Se re-fasea en el SRS combinado — no se construye todo a la vez pese a fusionar la
  especificación.
- El posicionamito de portafolio narra el sistema como "SaaS multi-tenant cuyo módulo
  clínico tiene el rigor de un sistema institucional real" — se evita el riesgo de
  narrativa diluida nombrado en la disidencia.
- Dos SRS de origen (CareLink v2.0, ClinicTrack v1.2.0) quedan archivados como insumo;
  el SRS combinado es la única fuente de verdad desde su aprobación.

## Adenda — 2026-08-03: Alcance de Milestone 1

**Decisión del autor:** M1 se amplía para incluir la totalidad del dominio clínico de
ClinicTrack (Paciente, Encuentro, Triage, Diario NANDA/NIC/NOC, Motor de Conocimiento,
Interconsultas, Labs, Farmacia) junto con Identity, en un solo milestone.

**Disidencia de Arch-Sentinel:** un milestone que solo se considera exitoso si se
completan 8 módulos funcionales para un desarrollador único no tiene un criterio de
éxito parcial — si el tiempo se corta antes del final, no hay nada demostrable. Se
recomendó mantener M1 acotado (Identity + Paciente + Encuentro + Audit log) y mover el
resto a M2+.

**Resolución de riesgo (sin revertir la decisión del autor):** M1 se subdivide
internamente en sub-fases con checkpoint verificable y demostrable cada una (vertical
slices), aunque el cierre formal de "Milestone 1" solo ocurra al completar todas. Esto
convierte el riesgo de "todo o nada" en degradación gradual: si el trabajo se detiene en
cualquier sub-fase, existe una demo funcional de lo construido hasta ahí. Ver estructura
de sub-fases en `tasks/todo.md` del SRS combinado.

## Frontend y despliegue (resuelto)
- **Frontend:** React 18 + Vite, SPA única con vistas por rol. Next.js descartado — no
  hay necesidad de SSR/SEO en una herramienta interna; portal de paciente autoservicio
  queda fuera de scope (no aplica al modelo ESE).
- **Despliegue:** sin demo público. Entornos reales: local (Docker Compose) + CI. Decisión
  revisitable si se decide publicar un demo más adelante.