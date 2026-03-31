# Data Model — CareLink

## Objetivo

Este documento describe entidades, constraints y decisiones de modelado.

## Multi-tenancy

- Estrategia: schema-per-tenant en PostgreSQL
- Esquema público reservado para registry de plataforma

## Entidades

Pendiente de completar por servicio y bounded context.

## Scheduling — Appointments

Tabla `appointments` (tenant schema):

- `id` UUID PK
- `tenant_id` UUID NOT NULL
- `physician_id` UUID NOT NULL
- `patient_id` UUID NOT NULL
- `slot_start` TIMESTAMP WITH TIME ZONE NOT NULL
- `duration_minutes` BIGINT NOT NULL
- `status` TEXT NOT NULL
- `version` BIGINT NOT NULL (optimistic locking)
- `created_at` TIMESTAMP WITH TIME ZONE

Constraints e índices:

- `idx_appointments_tenant_slot` en `(tenant_id, slot_start)`
- `uq_appointments_physician_slot_active` único parcial en
	`(physician_id, slot_start)` para estados activos
	`('PENDING', 'CONFIRMED', 'IN_PROGRESS')`

Nota:
- Se aplica soft delete por transición de estado (`CANCELLED`, `NO_SHOW`)
	en lugar de borrado físico.

## Clinical — Patients / Encounters / Audit

Modelo lógico de `patients` (tenant schema):

- `id` UUID PK
- `tenant_id` UUID NOT NULL
- `full_name` TEXT (cifrado)
- `phone` TEXT (cifrado)
- `email` TEXT (cifrado)
- `emergency_contact` TEXT (cifrado)
- `created_at` TIMESTAMPTZ

Modelo lógico de `encounters` (tenant schema):

- `id` UUID PK
- `tenant_id` UUID NOT NULL
- `patient_id` UUID NOT NULL
- `physician_id` UUID NOT NULL
- `chief_complaint` TEXT (cifrado)
- `physical_exam` TEXT (cifrado)
- `treatment_plan` TEXT (cifrado)
- `follow_up_instructions` TEXT (cifrado)
- `signed_at` TIMESTAMPTZ NULL
- `created_at` TIMESTAMPTZ

Modelo de auditoría `phi_audit_log`:

- `id` BIGSERIAL PK
- `tenant_id` UUID NOT NULL
- `actor_user_id` UUID NOT NULL
- `target_patient_id` UUID NOT NULL
- `action` VARCHAR(80) NOT NULL
- `occurred_at` TIMESTAMPTZ NOT NULL

Regla de integridad:

- `phi_audit_log` es append-only para `app_clinical_user`
  (`REVOKE UPDATE, DELETE`).
