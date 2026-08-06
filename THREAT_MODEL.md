# Threat model

Consolidado en [`docs/SRS.md` §8](docs/SRS.md) desde 2026-08-06.

Este archivo era un borrador de la era CareLink v2.0 (mencionaba Supabase Vault y
`pip-audit`, infraestructura que ADR-012/ADR-015 reemplazaron por Docker Compose local
sin demo público). Mantenerlo vivo en paralelo al modelo real —el que efectivamente
gobernó las decisiones de diseño de este proyecto— era el riesgo, no la ausencia de un
segundo documento: dos threat models pueden divergir, y solo uno de los dos importa.

Para la versión completa —superficie de ataque STRIDE, mitigación por vector, y los
criterios de aceptación verificables que salen de cada una (§18)— ver
[`docs/SRS.md` §8](docs/SRS.md). Para los hallazgos reales de una auditoría dinámica
contra el sistema corriendo, no un ejercicio de mesa, ver
[`docs/security/AUDIT-2026-08-06.md`](docs/security/AUDIT-2026-08-06.md).
