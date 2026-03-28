# CareLink — GitHub Copilot Agent Instructions

## Proyecto

CareLink es una plataforma SaaS clínica para consultorios médicos independientes.
Maneja datos de salud protegidos (PHI) bajo Res. 3100/2019, Ley 1581/2012 (Colombia),
GDPR (EU) e HIPAA (US). La especificación completa está en `docs/SRS.md`.

Stack:
- Identity / Scheduling / Clinical / Billing: Java 21 + Spring Boot 3.3 (Maven)
- Notification Service: Python 3.12 + FastAPI
- Physician Portal / Patient Portal: Node 20 + Next.js 14 App Router + TypeScript
- PostgreSQL 16 (schema-per-tenant) · Redis 7 · Kafka 3.7
- Infraestructura: Terraform + AWS (ECS Fargate, RDS, ElastiCache, MSK)

Estructura de servicios:
```
carelink/
├── services/
│   ├── identity-service/        # Spring Boot — puerto 8080
│   ├── scheduling-service/      # Spring Boot — puerto 8081
│   ├── clinical-service/        # Spring Boot — puerto 8082
│   ├── billing-service/         # Spring Boot — puerto 8083
│   └── notification-service/    # FastAPI    — puerto 8084
├── portals/
│   ├── physician-portal/        # Next.js    — puerto 3000
│   └── patient-portal/          # Next.js    — puerto 3001
├── terraform/
├── docs/
│   ├── SRS.md
│   ├── THREAT_MODEL.md
│   ├── SECURITY.md
│   └── adr/                     # ADR-001.md, ADR-002.md ...
└── tasks/
    ├── todo.md
    └── lessons.md
```

---

## PROTOCOLO OBLIGATORIO — ejecutar SIEMPRE antes de escribir código

Antes de implementar cualquier feature, endpoint, entidad o cambio de esquema,
responde estas secciones en el chat y espera mi confirmación.

### 1. Contexto arquitectónico

```
Bounded context afectado : [Identity | Scheduling | Clinical | Billing | Notification]
Servicio(s) que cambian  : [lista de servicios]
Capa(s) que se tocan     : [domain | application | infrastructure | portal]
Archivos nuevos          : [ruta exacta desde raíz del repo para cada archivo]
Archivos modificados     : [ruta exacta desde raíz del repo para cada archivo]
Eventos Kafka emitidos   : [NombreDelEvento o "ninguno"]
Eventos Kafka consumidos : [NombreDelEvento o "ninguno"]
```

### 2. Verificación de capas hexagonales

Confirma que la implementación respeta la dirección de dependencias:

```
¿El dominio importa infraestructura?  → debe ser NO siempre
¿Los puertos están en domain/?        → debe ser SÍ
¿Los adaptadores están en infra/?     → debe ser SÍ
¿El caso de uso orquesta sin conocer DB/Kafka? → debe ser SÍ
```

Si alguna respuesta viola la arquitectura, rediseña antes de continuar.

### 3. Verificación de bounded contexts

```
¿Este cambio importa clases de dominio de otro servicio? → debe ser NO
¿La comunicación cross-service usa Kafka o API Gateway?  → debe ser SÍ
¿El contrato del evento está en schemas/?                → debe ser SÍ si emite eventos
```

Si hay acoplamiento directo entre servicios, propón el diseño correcto antes de continuar.

### 4. Threat model del feature

Para cada feature nuevo, documenta en el chat:

```
Superficie de ataque nueva:
  - ¿Qué inputs acepta este feature que antes no existían?
  - ¿Qué datos expone que antes no se exponían?
  - ¿Qué operaciones privilegiadas habilita?

Amenazas identificadas (formato: Amenaza → Mitigación):
  - [amenaza 1] → [cómo se mitiga en esta implementación]
  - [amenaza 2] → [cómo se mitiga en esta implementación]

¿Requiere actualizar THREAT_MODEL.md? → [SÍ / NO + razón]
```

Si el feature toca PHI, añade:
```
Datos PHI involucrados   : [lista de campos]
Cifrado en reposo        : [AES-256-GCM con clave KMS por tenant]
Audit log requerido      : [SÍ — acción y campos a registrar]
Derecho de portabilidad  : [aplica exportación FHIR / PDF]
```

---

## Gestión de tareas

Al iniciar una tarea de 3 o más pasos:

1. Lee `tasks/todo.md` y `tasks/lessons.md` si existen
2. Ejecuta el Protocolo Obligatorio del bloque anterior
3. Escribe el plan con checkboxes en `tasks/todo.md`
4. Espera confirmación antes de ejecutar
5. Marca ítems completados conforme avanzas
6. Al terminar: añade revisión en `tasks/todo.md` y lecciones en `tasks/lessons.md`

---

## ADRs — cuándo y cómo crearlos

Crea un ADR en `docs/adr/ADR-NNN.md` cuando el cambio involucre cualquiera de:

- Elección entre dos o más patrones de diseño posibles
- Cambio en la forma en que dos servicios se comunican
- Nuevo mecanismo de seguridad o cifrado
- Cambio de esquema de base de datos con impacto en otros servicios
- Decisión sobre qué capa maneja una responsabilidad
- Trade-off documentado entre simplicidad y seguridad

Formato del ADR:

```markdown
# ADR-NNN — [Título corto]

## Estado
[Propuesto | Aceptado | Reemplazado por ADR-XXX]

## Contexto
[Qué problema o decisión necesita resolverse]

## Decisión
[Qué se decidió hacer]

## Alternativas consideradas
- [Alternativa 1] — descartada porque [razón]
- [Alternativa 2] — descartada porque [razón]

## Consecuencias
- Positivas: [lista]
- Negativas / trade-offs: [lista]

## Trigger de revisión
[Condición bajo la cual esta decisión debe re-evaluarse]
```

Antes de crear el ADR, preséntalo en el chat como borrador y espera confirmación.

---

## Security checklist — obligatorio antes de marcar cualquier tarea como done

No declares ninguna tarea completa sin verificar cada ítem:

```
ACCESO (OWASP A01)
[ ] Endpoint valida tenant_id del JWT antes de cualquier operación
[ ] Endpoint valida ownership del recurso (no solo rol)
[ ] Test automatizado: acceso cross-tenant devuelve 403
[ ] Test automatizado: rol sin permiso devuelve 403

CONFIGURACIÓN (OWASP A02)
[ ] Sin secretos hardcodeados — todo via variables de entorno
[ ] Variable nueva documentada en .env.example con descripción

DEPENDENCIAS (OWASP A03)
[ ] Sin dependencias nuevas sin justificación en el ADR o en el chat

CRIPTOGRAFÍA (OWASP A04)
[ ] Contraseñas: Argon2id — nunca MD5, SHA1, SHA256 sin salt
[ ] JWT: RS256 — nunca HS256 con secreto compartido
[ ] PHI en reposo: AES-256-GCM con clave KMS por tenant

INYECCIÓN (OWASP A05)
[ ] Toda entrada validada con Pydantic (Python) o Bean Validation (Java)
[ ] Sin concatenación de strings en queries
[ ] Test: input malicioso en cada campo nuevo devuelve 400

DISEÑO SEGURO (OWASP A06)
[ ] Comportamiento de fallo documentado: ¿qué pasa si la operación falla?
[ ] Fail-secure: el fallo no deja el sistema en estado inconsistente

AUTENTICACIÓN (OWASP A07)
[ ] Endpoint requiere JWT válido (no expirado, no revocado)
[ ] MFA verificado para roles PHYSICIAN y TENANT_ADMIN
[ ] Test: JWT expirado devuelve 401

INTEGRIDAD (OWASP A08)
[ ] Encuentros clínicos firmados no son modificables (test incluido)
[ ] Eventos Kafka tienen schema Avro validado

LOGGING (OWASP A09)
[ ] Requests logueados con trace_id, tenant_id (hashed), user_id (hashed)
[ ] Errores logueados completos internamente, sanitizados al cliente
[ ] PHI: ningún campo PHI aparece en logs operacionales

ERRORES (OWASP A10)
[ ] Todos los catch tienen tipo explícito — sin catch genérico vacío
[ ] Respuesta de error al cliente: código + mensaje genérico + request_id
[ ] Test: fallo interno devuelve 500 sin stack trace ni paths internos

PHI (adicional — Ley 1581 / HIPAA / GDPR)
[ ] Audit log emitido en cada lectura y escritura de PHI
[ ] Campos PHI cifrados antes de persistir
[ ] Export FHIR/PDF disponible si el feature expone datos del paciente
```

---

## Comandos por servicio

**Spring Boot (desde el directorio del servicio):**
```bash
./mvnw test
./mvnw checkstyle:check
./mvnw spring-boot:run
```

**Python — notification-service:**
```bash
pytest tests/ -v --tb=short
ruff check .
ruff format --check .
mypy app/
uvicorn app.main:app --reload --port 8084
```

**Next.js (physician-portal o patient-portal):**
```bash
npm run type-check
npm run lint
npm test
npm run dev
npm run build
```

Ejecuta los comandos del servicio afectado antes de marcar done.
Muestra el output completo. Si hay errores, corrígelos antes de continuar.

---

## Reglas de código

**Simplicidad:** mínimo cambio necesario. No toques lo que no hace falta.

**Sin parches:** busca la causa raíz. Si el fix se siente chapucero, implementa
la solución correcta.

**Verificación:** nunca digas que algo está listo sin haber ejecutado tests y linter.

**Commits:** en presente, pequeños, por servicio:
```
feat(scheduling): Add optimistic lock conflict resolution with alternatives
fix(clinical): Prevent PHI field exposure in error responses
security(identity): Enforce MFA check for PHYSICIAN role endpoints
adr(scheduling): Document optimistic locking decision ADR-004
```
No mezcles refactor con bug fix en el mismo commit.

---

## Patrones de código obligatorios

### Acceso a datos — valida tenant Y ownership (OWASP A01)

```java
// CORRECTO
// SECURITY: Validates tenant membership AND resource ownership (OWASP A01)
accessValidator.validate(currentUser.getTenantId(), patientId, currentUser.getRole());
Patient patient = patientRepository
    .findByTenantAndId(currentUser.getTenantId(), patientId)
    .orElseThrow(() -> new ResourceNotFoundException("PATIENT_NOT_FOUND"));

// INCORRECTO — sin validación de tenant
Patient patient = patientRepository.findById(patientId).orElseThrow(...);
```

### PHI Audit Log — obligatorio en todo acceso a datos de pacientes

```java
// SECURITY: PHI access logged — Ley 1581/2012, HIPAA §164.312(b), GDPR Art.30
auditLogService.record(
    user.getId(), patientId, AuditAction.READ, request.getSessionId()
);
```

### Queries — solo parametrizado, nunca concatenación (OWASP A05)

```java
// CORRECTO
@Query("SELECT p FROM Patient p WHERE p.tenantId = :tid AND p.id = :id")
Optional<Patient> findByTenantAndId(@Param("tid") UUID tid, @Param("id") UUID id);
```

```python
# CORRECTO — Pydantic valida todo input antes de la BD
class PatientQuery(BaseModel):
    tenant_id: UUID
    patient_id: UUID
    model_config = ConfigDict(strict=True)
```

### Errores — sanitizados al cliente, completos en logs (OWASP A10)

```java
// CORRECTO
try {
    return patientService.findById(tenantId, patientId);
} catch (DataAccessException e) {
    // SECURITY: Full error logged internally, sanitized response to client (OWASP A10)
    log.error("DB error patient={} tenant={}: {}", patientId, tenantId, e.getMessage());
    throw new ClinicalException("PATIENT_FETCH_ERROR", "Unable to retrieve patient record");
}
```

### Inmutabilidad de encuentros — Res. 3100/2019 + Ley 527/1999

```java
// SECURITY: Signed encounters immutable per Res. 3100/2019 and Ley 527/1999 (OWASP A08)
if (encounter.getSignedAt() != null) {
    throw new ImmutableRecordException("ENCOUNTER_ALREADY_SIGNED");
}
```

### PHI en reposo — cifrar antes de persistir (OWASP A04)

```java
// SECURITY: PHI encrypted with AES-256-GCM, per-tenant KMS key (OWASP A04)
patient.setFullName(encryptionService.encrypt(dto.getFullName(), tenantKey));
patient.setPhone(encryptionService.encrypt(dto.getPhone(), tenantKey));
```

---

## Reglas de arquitectura

### Bounded contexts — comunicación solo via Kafka o API Gateway

```java
// INCORRECTO — acoplamiento directo entre servicios
import com.carelink.clinical.domain.Patient; // en scheduling-service — PROHIBIDO

// CORRECTO — scheduling solo conoce un UUID
// Comunicación via evento Kafka o llamada al API Gateway
```

### Capas hexagonales — dirección de dependencias

```
domain/       ← sin dependencias de framework ni infraestructura
application/  ← orquesta domain, puede usar @Service
infrastructure/ ← adaptadores: JPA, Kafka, HTTP, email
```

```java
// INCORRECTO — infraestructura en dominio
import org.springframework.stereotype.Repository; // en domain/ — PROHIBIDO

// CORRECTO — puerto en dominio, adaptador en infraestructura
// domain/port/PatientRepository.java (interface pura)
// infrastructure/persistence/JpaPatientRepository.java (implementa el puerto)
```

---

## Documentación obligatoria por tipo de cambio

| Cambio | Qué actualizar |
|--------|----------------|
| Nuevo endpoint | `docs/API.md` — ruta, rol, request, response, errores posibles |
| Nueva entidad de dominio | `docs/DATA_MODEL.md` — diagrama y constraints |
| Decisión arquitectónica | `docs/adr/ADR-NNN.md` — presentar borrador antes de crear |
| Cambio de esquema de BD | `migrations/` con comentario del motivo |
| Nueva variable de entorno | `.env.example` con descripción y origen del valor |
| Cambio en evento Kafka | Schema Avro en `schemas/` + `CHANGELOG.md` del schema |
| Nueva mitigación de seguridad | `SECURITY.md` con ítem OWASP que mitiga y test que lo verifica |
| Nuevo threat identificado | `THREAT_MODEL.md` con vector, impacto y mitigación |

---

## Límites

- Solo modifica archivos necesarios para la tarea
- Si detectas un problema fuera del alcance, menciónalo sin implementarlo
- Si un cambio afecta múltiples servicios, presenta el plan completo antes de ejecutar
- Antes de crear cualquier archivo, confirma la ruta exacta desde la raíz del repo