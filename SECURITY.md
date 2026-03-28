Security checklist (inicial)

Este archivo documenta las mitigaciones básicas requeridas por el proyecto.

- Autenticación: JWT RS256 para emisión de access tokens; refresh tokens HttpOnly.
- Hashing de contraseñas: Argon2id recomendado.
- PHI: cifrado en aplicación con AES-256-GCM y claves por tenant (KMS).
- Logging: sanitizar PHI en logs operacionales; incluir trace_id y tenant_id (hashed).
- OWASP: aplicar validación de entrada con Bean Validation / Pydantic; evitar concatenación en queries.
- Dependencias: escaneo con Trivy y Gitleaks en CI.

Referencias y checklist completo en `docs/SECURITY.md` (por completar).
