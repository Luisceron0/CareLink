# API Contract — CareLink

## Objetivo

Este documento centraliza contratos de endpoints expuestos por el sistema.

## Convención

- Base path: `/api/v1`
- Auth: `Authorization: Bearer <access_token>`
- Contexto de tenant: derivado del JWT, nunca de parámetro de request

## Endpoints

Pendiente de completar por bounded context conforme se implementen.

### Identity / Authentication

#### POST /api/v1/auth/register
- **Role:** public (no auth required)
- **Description:** Registra un nuevo usuario mínimo. En producción este endpoint debe cifrar PHI, persistir por tenant y emitir audit log.
- **Request body:**

```json
{
	"email": "user@example.com",
	"password": "string (min 8 chars)"
}
```

- **Responses:**
	- `201 Created` — body:

```json
{
	"id": "<uuid>",
	"email": "user@example.com"
}
```
	- `422 Unprocessable Entity` — validation errors

	#### POST /api/v1/auth/login
	- **Role:** public
	- **Description:** Login con email y password. Devuelve JWT que se debe usar en `Authorization: Bearer <token>`.
	- **Request body:**

	```json
	{
		"email": "user@example.com",
		"password": "string (min 8 chars)"
	}
	```

	- **Responses:**
		- `200 OK` — body:

	```json
	{
		"access_token": "<jwt>",
		"token_type": "bearer"
	}
	```
		- `401 Unauthorized` — credenciales inválidas

	#### POST /api/v1/tenants/register
	- **Role:** public (used for tenant self-registration)
	- **Description:** Registra un nuevo tenant (mínimo). En producción este endpoint debe crear el esquema de tenant, asignar `TENANT_ADMIN`, enviar verificación por email y emitir audit log.
	- **Request body:**

	```json
	{
		"legal_name": "Clinica Santa Maria",
		"tax_id": "900123456-7",
		"contact_email": "admin@clinicamaria.com",
		"country": "CO",
		"timezone": "America/Bogota"
	}
	```

	- **Responses:**
		- `201 Created` — body:

	```json
	{
		"id": "<uuid>",
		"slug": "clinica-santa-maria",
		"contact_email": "admin@clinicamaria.com"
	}
	```
		- `422 Unprocessable Entity` — validation errors

### Scheduling / Appointments

#### POST /api/v1/appointments
- **Role:** `RECEPTIONIST`, `TENANT_ADMIN`
- **Headers:** `X-Tenant-Id`, `X-User-Role`
- **Description:** Reserva una cita y emite evento `AppointmentBooked` en topic `appointments`.
- **Request body:**

```json
{
	"physicianId": "<uuid>",
	"patientId": "<uuid>",
	"slotStart": "2026-04-01T09:00:00",
	"durationMinutes": 30
}
```

- **Responses:**
	- `200 OK` — cita creada
	- `409 Conflict` — slot ocupado (debe incluir 3 alternativas)
	- `403 Forbidden` — rol no autorizado

#### GET /api/v1/appointments
- **Role:** `RECEPTIONIST`, `TENANT_ADMIN`, `PHYSICIAN`
- **Headers:** `X-Tenant-Id`
- **Description:** Lista citas del tenant con filtros opcionales.
- **Query params opcionales:** `physicianId`, `date` (`YYYY-MM-DD`), `status`
- **Responses:**
	- `200 OK` — lista de citas

#### PATCH /api/v1/appointments/{id}/status
- **Role:**
	- `PHYSICIAN` para `IN_PROGRESS` y `COMPLETED`
	- `RECEPTIONIST`/`TENANT_ADMIN` para `CANCELLED`
- **Headers:** `X-Tenant-Id`, `X-User-Role`
- **Description:** Actualiza estado de una cita y emite evento `AppointmentStatusChanged`.
- **Request body:**

```json
{
	"status": "CONFIRMED"
}
```

- **Responses:**
	- `200 OK` — cita actualizada
	- `403 Forbidden` — transición o rol inválido
	- `404 Not Found` — cita inexistente en el tenant

#### DELETE /api/v1/appointments/{id}
- **Role:** `RECEPTIONIST`, `TENANT_ADMIN`
- **Headers:** `X-Tenant-Id`, `X-User-Role`
- **Description:** Cancelación (soft delete por estado) y emisión de `AppointmentCancelled`.
- **Responses:**
	- `200 OK` — cita cancelada
	- `403 Forbidden` — rol no autorizado

### Clinical / Patients and Encounters

#### POST /api/v1/patients
- **Role:** `PHYSICIAN`, `RECEPTIONIST`, `TENANT_ADMIN`
- **Headers:** `X-Tenant-Id`, `X-User-Id`, `X-User-Role`
- **Description:** Registra paciente con PHI cifrado en reposo.
- **Responses:**
	- `200 OK` — paciente registrado
	- `400 Bad Request` — payload inválido

#### GET /api/v1/patients/{id}
- **Role:** `PHYSICIAN`, `TENANT_ADMIN`
- **Headers:** `X-Tenant-Id`, `X-User-Id`, `X-User-Role`
- **Description:** Descifra y devuelve datos PHI del paciente.
- **Responses:**
	- `200 OK` — paciente
	- `403 Forbidden` — acceso cross-tenant o rol no autorizado
	- `404 Not Found` — recurso inexistente

#### POST /api/v1/patients/{id}/encounters
- **Role:** `PHYSICIAN`, `TENANT_ADMIN`
- **Headers:** `X-Tenant-Id`, `X-User-Id`, `X-User-Role`
- **Description:** Crea un encuentro clínico para el paciente.

#### GET /api/v1/patients/{id}/encounters/{eid}
- **Role:** `PHYSICIAN`, `TENANT_ADMIN`
- **Headers:** `X-Tenant-Id`, `X-User-Id`, `X-User-Role`
- **Description:** Recupera encuentro clínico descifrado.

#### PUT /api/v1/patients/{id}/encounters/{eid}
- **Role:** `PHYSICIAN`, `TENANT_ADMIN`
- **Headers:** `X-Tenant-Id`, `X-User-Id`, `X-User-Role`
- **Description:** Actualiza encuentro no firmado.
- **Responses:**
	- `200 OK` — actualizado
	- `409 Conflict` — intento de modificar encuentro firmado

#### POST /api/v1/patients/{id}/encounters/{eid}/sign
- **Role:** `PHYSICIAN`, `TENANT_ADMIN`
- **Headers:** `X-Tenant-Id`, `X-User-Id`, `X-User-Role`
- **Description:** Firma y bloquea el encuentro clínico.

#### GET /api/v1/patients/{id}/export/pdf
- **Role:** `PHYSICIAN`, `TENANT_ADMIN`
- **Headers:** `X-Tenant-Id`, `X-User-Id`, `X-User-Role`
- **Description:** Exporta resumen clínico en PDF.

#### GET /api/v1/patients/{id}/export/fhir
- **Role:** `PHYSICIAN`, `TENANT_ADMIN`
- **Headers:** `X-Tenant-Id`, `X-User-Id`, `X-User-Role`
- **Description:** Exporta datos clínicos en FHIR R4 JSON.

#### POST /api/v1/patients/{id}/gdpr-request
- **Role:** `TENANT_ADMIN`, `PHYSICIAN`
- **Headers:** `X-Tenant-Id`, `X-User-Id`, `X-User-Role`
- **Description:** Procesa solicitud GDPR/habeas data por jurisdicción.
- **Request body:**

```json
{
	"requestType": "ERASURE",
	"jurisdiction": "EU",
	"confirmed": true
}
```

- **Reglas clave:**
	- `EU + ERASURE` exige `confirmed=true` para ejecutar eliminación
	  irreversible de identidad desacoplada.
	- `CO` siempre responde `200` con base legal de retención clínica.

- **Responses:**
	- `200 OK` — decisión aplicada (`RETAINED` o `PSEUDONYMIZED`).
	- `400 Bad Request` — jurisdicción/tipo inválido o falta confirmación EU.
	- `403 Forbidden` — rol no autorizado o intento cross-tenant.
	- `404 Not Found` — paciente inexistente en tenant.

