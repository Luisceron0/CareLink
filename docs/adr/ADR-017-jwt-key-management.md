# ADR-017 — JWT Key Management & Refresh Token Rotation

## Estado
**Aceptado — implementado.** `JwtKeyProvider`, `StaticKeyProvider`, `JwksKeyProvider` y
`VaultKeyProvider` existen en `services/identity-service/.../infrastructure/security`,
con cobertura en `VaultKeyProviderTest` y `JwksAndRefreshIntegrationTest`.

## Nota de numeración (2026-08-04)
Este documento vivía como `ADR-00X-jwt-management.md`, sin número asignado. Un ADR sin
número no se puede referenciar desde el SRS ni desde otro ADR, que es la única razón por
la que existe la numeración. Pasa a ADR-017.

## Relación con ADR-004
ADR-004 decide *qué* mecanismo de revocación se adopta (JWKS respaldado por Vault, en vez
de una tabla `revoked_tokens`). Este ADR detalla *cómo* se estructura: el puerto
`JwtKeyProvider` y sus adaptadores. No compiten; ADR-004 es la decisión, ADR-017 el
diseño que la implementa.

## Corrección respecto del texto original
El punto 4 dice que los refresh tokens se almacenan "en la BD/Redis". **Redis no forma
parte del stack** (SRS §9 — nada de §5 lo requiere; ADR-016 quedó superado). El
almacenamiento es PostgreSQL. El resto del ADR —hash HMAC-SHA256 y rotación one-time-use—
se mantiene sin cambios.

## Contexto
El SRS exige JWTs firmados con RS256 para access tokens y refresh tokens rotados on‑use (15m access, 7d refresh). Para ser seguras y sostenibles, las claves privadas no deben residir en el código ni en variables de entorno en texto claro en producción. Además, la validación de tokens debe soportar rotación de claves (`kid`) y fetch seguro de claves públicas (JWKS) cuando sean necesarias.

## Decisión
1. Introducir un puerto `JwtKeyProvider` en `domain.port` que abstraiga la obtención de claves.
2. Implementar dos adaptadores en `infrastructure/security`:
   - `StaticKeyProvider`: generación en memoria para entornos de desarrollo y tests.
   - `JwksKeyProvider`: consumidor de JWKS con cache y TTL configurable (lectura solo de claves públicas).
3. `JwtService` usará `JwtKeyProvider` para firmar y verificar tokens; firmará con la clave privada del proveedor (si está disponible) y verificará usando `kid` contra las claves públicas del proveedor.
4. Refresh tokens se almacenarán hasheadas (HMAC-SHA256) en la BD/Redis; al refresh se emitirá un nuevo token y se invalidará (eliminará) el anterior (rotación one‑time‑use).
5. En producción, las claves privadas deben almacenarse en un KMS/Vault (Supabase Vault o proveedor equivalente). La integración con Vault será implementada en un adaptador futuro que implementará `JwtKeyProvider` y leerá claves privadas de forma segura.

## Alternativas consideradas
- Almacenar claves privadas en variables de entorno — descartada (riesgo alto de fuga y rotación manual).
- Usar solo HS256 con secreto compartido — descartada (contradice SRS que exige RS256 y mayor seguridad con asimétrico).
- No hashear refresh tokens y guardarlos tal cual — descartada (riesgo de exposición y replay).

## Consecuencias
- Positivas:
  - Cumplimiento con SRS y mejores prácticas (RS256, `kid`, JWKS).
  - Claves privadas protegidas por KMS en producción.
  - Refresh tokens no reutilizables (rotación on‑use) y seguros en reposo.
- Negativas / trade‑offs:
  - Se requiere infra adicional (Vault/KMS) y configuración por entorno.
  - Mayor complejidad en la inicialización y pruebas de integración; se mitigará con `StaticKeyProvider` en dev.

## Migación / Rollout
1. Merge de los cambios con `StaticKeyProvider` por defecto (ya incluido en dev branch).
2. Añadir `JwksKeyProvider` y configurar URL en `JWT_JWKS_URL` para entornos que consuman JWKS.
3. Implementar adaptador `VaultKeyProvider` que cargue la clave privada desde Supabase Vault y lo desplegar en staging.
4. Rotación de claves: emitir nueva clave en Vault, publicar su `kid` en JWKS, desplegar nuevo signer apuntando a la nueva `kid`, y luego retirar la clave antigua después de un periodo de gracia.

## Seguridad y pruebas
- Tests unitarios para `JwtService` que validen firma/verificación usando `StaticKeyProvider`.
- Test de integración (staging) que valide JWKS fetch, `kid` handling y rotación.
- Test de contrato: refresh token rotation y revocación (no replay).

## Referencias
- SRS: FR‑ID‑03, FR‑ID‑04 (tokens y sesiones)
- OWASP Crypto Guidelines
