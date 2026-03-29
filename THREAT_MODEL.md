THREAT MODEL — inicio

Superficie de ataque nueva (resumen):
- Endpoints de registro y login (public-facing)
- Proceso de provisionamiento de tenant (creación de esquemas DB)
- Exportación y manejo de PHI (lectura/descarga)

Amenazas identificadas y mitigaciones (inicio):
- Registro masivo / abuso → rate limiting por IP + captcha opcional
- Inyección SQL en queries dinámicas → uso de queries parametrizadas y PReparedStatements
- Acceso cross-tenant → validar tenant_id desde JWT en cada request
- Exposición de PHI en logs → sanitizar logs y evitar imprimir campos PHI

¿Requiere update del THREAT_MODEL global? SÍ — expandir con vectores por servicio y mitigaciones técnicas.

Notas: este documento es un starter; cada feature añadirá sus vectores específicos.

---

JWT / Refresh Tokens / JWKS (añadido)

Superficie de ataque nueva:
- Exposición de clave privada (config/secret leak)
- Manipulación de JWKS (MITM o JWKS comprometido)
- Replay de refresh tokens si no se rotan correctamente
- Algoritmo‑confusion (tokens con `alg:none` o cambio a HS256)

Amenazas y mitigaciones:
- Clave privada expuesta → Mitigación: almacenar claves privadas en Supabase Vault/KMS; acceso limitado por rol; auditoría de accesos; no incluir claves en repositorios ni en variables de entorno en producción.
- JWKS malicioso / MITM → Mitigación: solo fetch desde URLs HTTPS validadas, validar issuer/audience, soportar pinning opcional (thumbprint) y cache con TTL; monitorizar cambios inusuales en JWKS.
- Replay de refresh tokens → Mitigación: almacenar refresh tokens hasheados (HMAC-SHA256), rotación on‑use (issue new + revoke old), expiración corta y limite concurrent sessions; auditar refresh/issue events.
- Algoritmo confusion → Mitigación: aceptar solo RS256 en verificación; rechazar tokens con `alg` distinto o con missing `kid` when required.

Operaciones y requisitos de despliegue:
- `.env.example` debe documentar variables: `JWT_ACCESS_TTL`, `JWT_JWKS_URL`, `REFRESH_TOKEN_HMAC_SECRET`, `REFRESH_TOKEN_TTL_SECONDS`, `MAX_CONCURRENT_SESSIONS`.
- Implementar adaptador `VaultKeyProvider` que lea la clave privada directamente desde Vault para producción.
- Añadir alertas en monitoring si la lista de `kid` cambia inesperadamente o si verificaciones fallan masivamente.

---

Scheduling / Availability (nueva superficie)

Superficie de ataque nueva:
- Endpoints de disponibilidad: `POST /api/v1/physicians/{id}/availability`, `GET /api/v1/physicians/{id}/availability` (public para tenants autenticados)
- Inputs: `tenant_id`, `physician_id`, `day`, `start_time`, `end_time`

Amenazas y mitigaciones:
- Acceso cross-tenant → Mitigación: validar `tenant_id` extraído del JWT en la capa de aplicación (`AvailabilityService`) antes de cualquier llamada a repositorios; tests automáticos que prueban acceso cross-tenant -> 403/401.
- Falsificación/escrow de disponibilidad (usuario crea bloques para otro doctor) → Mitigación: ownership enforced en `AvailabilityService` y en queries JPA (`findByIdAndTenantId`), validación adicional en controladores y pruebas.
- Condiciones de carrera / double-booking → Mitigación: usar transacciones y optimistic locking en entidades críticas (añadir `@Version` si es necesario para `TimeSlot`/`Appointment`), aplicar verificación de conflictos en `SlotCalculator`/caso de uso y pruebas de concurrencia con zonky embedded DB.
- Input malformed / timezone bugs → Mitigación: validar formatos y rangos (Bean Validation), normalizar zonas horarias en server (UTC) y documentar en API.
- DoS en endpoints de escritura (spam de creación de bloques) → Mitigación: rate limiting por tenant/user e instrumentación de métricas y alertas para picos.
- Tampering de datos en transporte → Mitigación: exigir HTTPS y validar JWTs firmados con RS256; asegurar cabeceras CORS apropiadas.

Operaciones y requisitos de despliegue:
- Añadir pruebas de integración con `@AutoConfigureEmbeddedDatabase(provider = DatabaseProvider.ZONKY)` para validar comportamiento real de Postgres sin Docker.
- Asegurar que los adaptadores JPA estén en `infrastructure/persistence/` y que el `AvailabilityService` no importe infraestructura.
- Auditoría: registrar cambios de disponibilidad (create/update/delete) con trace_id, tenant_id (hashed) y user_id (hashed); los logs no deben contener PHI.


