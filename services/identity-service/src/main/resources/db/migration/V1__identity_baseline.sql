-- V1 — Línea base del schema público: Identity.
--
-- Consolida los dos archivos previos de migrations/, que creaban `tenants` y
-- `users` con formas incompatibles entre sí (`legal_name`/`contact_email` en uno,
-- `name`/`role`/`created_at` en el otro) y ambos con IF NOT EXISTS, de modo que
-- el esquema resultante dependía de cuál corriera primero. Ninguno se había
-- aplicado nunca a una base real —la aplicación no arrancaba—, así que no hay
-- datos que migrar y esta es una línea base limpia, no una reconciliación.
--
-- La forma de las tablas es la que las entidades JPA ya esperan; se derivó de
-- ellas, no al revés.
--
-- El schema público contiene solo Identity. Todo lo clínico —incluido `audit_log`
-- (SRS §10)— vive dentro del schema de cada tenant, provisionado aparte.

CREATE TABLE tenants (
    id          UUID PRIMARY KEY,
    name        TEXT        NOT NULL,
    slug        TEXT        NOT NULL UNIQUE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),

    -- Misma invariante que el value object TenantSlug del dominio. El slug se
    -- concatena en un CREATE SCHEMA al provisionar, así que la restricción se
    -- repite en la base: si algún día un caller construye el slug sin pasar por
    -- el tipo, el INSERT falla antes de llegar al DDL. Ver ADR-010 y AC-05.
    CONSTRAINT tenants_slug_format CHECK (slug ~ '^[a-z0-9-]{3,64}$')
);

CREATE TABLE users (
    id          UUID PRIMARY KEY,
    tenant_id   UUID        REFERENCES tenants(id) ON DELETE RESTRICT,
    email       TEXT        NOT NULL UNIQUE,
    role        TEXT        NOT NULL,
    password    TEXT        NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- ON DELETE RESTRICT y no CASCADE: FR-ID-02 dice que desactivar un usuario retiene
-- su historial de auditoría permanentemente. Un borrado en cascada de tenant que
-- se lleve usuarios por delante contradice esa retención.

CREATE INDEX idx_users_tenant_id ON users (tenant_id);

CREATE TABLE sessions (
    id             UUID PRIMARY KEY,
    user_id        UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    refresh_token  TEXT,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_sessions_user_id ON sessions (user_id);

CREATE TABLE verification_tokens (
    id          UUID PRIMARY KEY,
    -- Hash del token, nunca el token en claro: leer esta tabla no debe alcanzar
    -- para verificar cuentas ajenas.
    token_hash  TEXT        NOT NULL UNIQUE,
    user_id     UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
