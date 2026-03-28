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

