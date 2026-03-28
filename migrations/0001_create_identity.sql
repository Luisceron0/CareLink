-- Migration: create tenants and users tables (Postgres dialect)
-- SECURITY: This migration creates identity core tables. Use Alembic in CI for real deployments.

CREATE TABLE IF NOT EXISTS tenants (
  id uuid PRIMARY KEY,
  legal_name text NOT NULL,
  slug text NOT NULL UNIQUE,
  contact_email text NOT NULL
);

CREATE TABLE IF NOT EXISTS users (
  id uuid PRIMARY KEY,
  email text NOT NULL UNIQUE,
  password_hash text NOT NULL,
  tenant_id uuid NULL REFERENCES tenants(id)
);
