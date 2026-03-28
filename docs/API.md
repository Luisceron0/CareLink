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

