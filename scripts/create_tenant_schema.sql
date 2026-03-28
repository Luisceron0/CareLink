-- Script to create tenant schema from template
-- Usage (psql): \i scripts/create_tenant_schema.sql

-- Params expected: :tenant_slug

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_namespace WHERE nspname = 'tenant_' || current_setting('app.tenant_slug', true)) THEN
        EXECUTE format('CREATE SCHEMA tenant_%I', current_setting('app.tenant_slug', true));
        -- apply template
        EXECUTE format('SET search_path TO tenant_%I; \i migrations/002_tenant_schema_template.sql', current_setting('app.tenant_slug', true));
    END IF;
END$$;
