# TODO — CareLink

## Sesión actual

- [x] Crear archivo de instrucciones de Copilot en `.github/copilot-instructions.md`
- [x] Crear carpeta `tasks/` con archivos persistentes
- [x] Crear `tasks/lessons.md`
- [ ] Verificar estructura documental base (`docs/SRS.md`, `docs/API.md`, `docs/DATA_MODEL.md`, `docs/adr/`)
- [ ] Definir siguiente tarea de desarrollo (MVP vertical slice)

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

