# Seguridad

Este es un repositorio de portafolio: no maneja información de salud real, no tiene
entorno de producción, y no acepta reportes de vulnerabilidad para un sistema en vivo
porque no hay ninguno corriendo fuera de lo que cada quien levanta localmente. Dicho eso,
el modelo de amenazas y los controles se tomaron en serio — esta página es el mapa de
dónde está cada cosa.

## Dónde está cada cosa

| Qué buscás | Dónde está |
|---|---|
| Modelo de amenazas completo (STRIDE) | [`docs/SRS.md` §8](docs/SRS.md) |
| Criterios de aceptación de seguridad, con estado Pass/Fail | [`docs/SRS.md` §18](docs/SRS.md) |
| Auditoría end-to-end (Sub-fase 8): hallazgos, severidad, corrección, evidencia | [`docs/security/AUDIT-2026-08-06.md`](docs/security/AUDIT-2026-08-06.md) |
| Decisiones de seguridad con su razonamiento (ADRs) | [`docs/SRS.md` §17](docs/SRS.md) |
| Historial de hallazgos y correcciones, en el momento en que pasaron | [`tasks/lessons.md`](tasks/lessons.md) |
| Reglas SAST propias (SQL/JPQL dinámico) | [`.semgrep/`](.semgrep/) |
| Gates de CI bloqueantes | [`.github/workflows/ci.yml`](.github/workflows/ci.yml) |

## Controles centrales, en una línea cada uno

- **Aislamiento por tenant**: schema-per-tenant en PostgreSQL — un cross-tenant no es un
  chequeo en runtime que pueda tener un bug, es que el dato para pedir lo ajeno no existe
  en la forma del endpoint (el tenant sale siempre del JWT, nunca de un parámetro).
- **Aislamiento por servicio dentro del tenant**: filtro en el `WHERE`/`HAVING` de cada
  consulta, nunca sobre filas ya traídas. Un rol sin `service_id` asignado no ve nada —
  falla cerrado, no abierto.
- **Revocación de acceso por interconsulta**: se revalida en cada request contra el
  estado actual; no existe un permiso persistido que pueda quedar desactualizado.
- **k-anonimato en el Motor de Conocimiento**: `HAVING COUNT(DISTINCT patient_id) >= k`
  dentro de la consulta — las filas por debajo del umbral nunca salen de la base.
- **Historia clínica inmutable tras la firma** y **audit log append-only**: ambos
  aplicados con triggers de PostgreSQL, para cualquier rol que conecte, incluido el
  administrador — no solo lógica de aplicación.
- **PHI cifrada en reposo**: AES-256-GCM, IV aleatorio por operación, clave derivada por
  tenant.
- **Secretos**: la aplicación se niega a arrancar sin ellos configurados. Ver
  `docs/security/AUDIT-2026-08-06.md` (H-01) para el caso real en el que esto falló y
  cómo se corrigió.

## Reportar algo

Si encontrás algo real en este código (no en un despliegue, porque no hay ninguno
público), abrí un issue. Si preferís no hacerlo público primero, el perfil de GitHub
tiene el contacto.
