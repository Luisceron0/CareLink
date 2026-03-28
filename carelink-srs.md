# CareLink — Software Requirements Specification (SRS)

**Version:** 1.0 — MVP  
**Status:** Draft  
**Author:** Luis Alejandro Cerón Muñoz  
**Consultora:** ElevaForge  
**Date:** March 2026  
**Stack:** Java/Spring Boot · Python/FastAPI · Next.js · PostgreSQL · Redis · Kafka  
**Repository:** github.com/luisceron0/carelink  

---

## Table of Contents

1. [Introduction](#1-introduction)
2. [Problem and Market Context](#2-problem-and-market-context)
3. [System Overview](#3-system-overview)
4. [User Roles and Personas](#4-user-roles-and-personas)
5. [Functional Requirements](#5-functional-requirements)
6. [Non-Functional Requirements](#6-non-functional-requirements)
7. [Regulatory Compliance Framework](#7-regulatory-compliance-framework)
8. [Security Architecture — OWASP 2025](#8-security-architecture--owasp-2025)
9. [Technology Stack](#9-technology-stack)
10. [Data Model](#10-data-model)
11. [API Specification](#11-api-specification)
12. [Internationalization](#12-internationalization)
13. [Testing Strategy](#13-testing-strategy)
14. [Observability](#14-observability)
15. [Deployment Architecture](#15-deployment-architecture)
16. [MVP Scope](#16-mvp-scope)
17. [Architectural Decisions (ADRs)](#17-architectural-decisions-adrs)
18. [Acceptance Criteria](#18-acceptance-criteria)
19. [Glossary](#19-glossary)

---

## 1. Introduction

### 1.1 Purpose

This SRS defines the complete requirements for CareLink, a clinical coordination platform for
independent medical offices and small clinics. It serves as the primary reference for design,
implementation, QA, security review, and stakeholder communication. Every architectural and
security decision made during development must be traceable to a requirement in this document.

### 1.2 ElevaForge Alignment

CareLink embodies the ElevaForge philosophy directly:

- **Seguridad desde el diseño** — compliance and security are first-class architecture concerns,
  not post-launch additions.
- **Autonomía operativa garantizada** — physicians and receptionists operate the platform
  without ongoing technical support after onboarding.
- **Transparencia total** — every action in the system is auditable and explainable to
  the user in plain language.
- **Propiedad 100% del cliente** — multi-tenant architecture ensures each clinic owns and
  can export their own data at any time.

### 1.3 Scope

CareLink is a SaaS platform that enables independent medical offices and small clinics to:

- Manage patient scheduling with real-time availability
- Maintain structured electronic clinical records (Historia Clínica Electrónica)
- Handle billing and generate RIPS reports for Colombia
- Send automated appointment reminders
- Give patients a self-service portal to view their own records
- Generate operational and financial reports for clinic administrators

The system targets LATAM initially (Colombia as primary market) with i18n architecture
ready for USA and EU expansion from day one.

### 1.4 Definitions

| Term | Definition |
|------|-----------|
| HC / HCE | Historia Clínica / Historia Clínica Electrónica — patient clinical record |
| PHI | Protected Health Information — any individually identifiable health data |
| REPS | Registro Especial de Prestadores de Servicios de Salud (Colombia) |
| RIPS | Registro Individual de Prestación de Servicios (Colombia billing standard) |
| IPS | Institución Prestadora de Servicios de Salud (Colombia healthcare provider) |
| SOGCS | Sistema Obligatorio de Garantía de Calidad de la Atención de Salud |
| i18n | Internationalization — designing for multiple languages and regions |
| l10n | Localization — adapting to a specific locale |
| RBAC | Role-Based Access Control |
| Tenant | A registered clinic or medical office using the platform |
| PHI Audit Log | Immutable record of every access to patient health information |

### 1.5 References

- Resolución 3100/2019 — MinSalud Colombia (habilitación de servicios de salud)
- Resolución 1888/2025 — MinSalud Colombia (interoperabilidad HCE, Resumen Digital de Atención)
- Resolución 2275/2023 — MinSalud Colombia (RIPS en formato JSON)
- Ley 1581/2012 — Colombia (Habeas Data, protección de datos personales)
- Ley 527/1999 — Colombia (firma digital y mensajes de datos)
- GDPR (EU) 2016/679 — for EU market expansion
- HIPAA (US) 45 CFR Parts 160 and 164 — for US market expansion
- OWASP Top 10 2025 — owasp.org/Top10
- HL7 FHIR R4 — interoperability standard for clinical data

---

## 2. Problem and Market Context

### 2.1 The Real Problem

Independent medical offices and small clinics in LATAM manage patient operations with a
combination of paper records, spreadsheets, WhatsApp groups, and phone calls. The consequences
are direct and measurable:

- **Patient safety risk:** no reliable medication history means dangerous prescription conflicts
- **Revenue leakage:** unbilled services, lost appointments, and unrecovered fees due to
  no-shows without reminders
- **Regulatory exposure:** paper records that don't comply with Res. 3100/2019 and the new
  Res. 1888/2025 interoperability mandate are a liability
- **Operational bottleneck:** receptionists lose hours per week resolving scheduling
  conflicts that a system would prevent automatically

### 2.2 Market Gap

Enterprise solutions (Epic, Medifolios enterprise tier) cost hundreds of thousands of dollars
annually and require months of implementation — completely out of reach for a clinic with
2-5 physicians. Free tools lack clinical structure, regulatory compliance, and audit trails.
CareLink targets the underserved middle: structured, compliant, and affordable.

### 2.3 Why This Is Technically Challenging

CareLink is not a CRUD application. The difficulty lies in:

1. **Concurrent availability management** — two receptionists cannot book the same slot
   simultaneously. Requires optimistic locking with conflict resolution UX.
2. **Regulatory auditability** — every read and write to PHI must be logged immutably.
   The log itself is a compliance artifact, not just an operational tool.
3. **Multi-tenancy with strict isolation** — one misconfigured query exposing data from
   Clinic A to Clinic B is a catastrophic compliance failure.
4. **i18n from the data layer** — dates, currencies, phone formats, and regulatory fields
   differ between Colombia, USA, and EU. This must be handled in the domain, not just the UI.
5. **Conflict between GDPR right to erasure and medical record retention** — Colombian law
   requires 15-year retention of HC. GDPR requires deletion on request. The architecture
   must resolve this tension by design.

---

## 3. System Overview

### 3.1 Product Identity

- **Name:** CareLink
- **Tagline (ES):** La clínica organizada, el paciente tranquilo.
- **Tagline (EN):** Organized care, confident patients.

### 3.2 Architecture Overview

CareLink uses a **Hexagonal (Ports and Adapters) architecture** with **Clean Architecture
layers** inside each bounded context. The system is structured around four domain services
that communicate asynchronously via events.

```
┌─────────────────────────────────────────────────────────────┐
│                     CLIENT LAYER                            │
│  Next.js (Physician Portal)  │  Next.js (Patient Portal)   │
│  React PWA (Receptionist)    │  (separate deployments)      │
└──────────────────┬──────────────────────┬───────────────────┘
                   │                      │
┌──────────────────▼──────────────────────▼───────────────────┐
│                   API GATEWAY (Spring Boot)                  │
│  Auth filter · Rate limiting · Request routing · Audit log  │
└──────┬──────────────┬──────────────┬──────────────┬─────────┘
       │              │              │              │
┌──────▼──────┐ ┌─────▼──────┐ ┌────▼──────┐ ┌────▼──────────┐
│  Scheduling │ │  Clinical  │ │  Billing  │ │ Notification  │
│  Service    │ │  Records   │ │  Service  │ │   Service     │
│ (Spring)    │ │  Service   │ │ (Spring)  │ │  (FastAPI)    │
│             │ │ (Spring)   │ │           │ │               │
└──────┬──────┘ └─────┬──────┘ └────┬──────┘ └────┬──────────┘
       │              │              │              │
┌──────▼──────────────▼──────────────▼──────────────▼─────────┐
│                    EVENT BUS (Kafka)                          │
│  AppointmentBooked · AppointmentCancelled · RecordUpdated   │
│  InvoiceGenerated · ReminderRequested                        │
└──────────────────────────────────────────────────────────────┘
       │              │              │              │
┌──────▼──────┐ ┌─────▼──────┐ ┌────▼──────┐ ┌────▼──────────┐
│ PostgreSQL  │ │ PostgreSQL │ │PostgreSQL │ │  PostgreSQL   │
│ (schedules) │ │ (records)  │ │ (billing) │ │  (notifs)     │
└─────────────┘ └────────────┘ └───────────┘ └───────────────┘
```

### 3.3 Bounded Contexts

| Context | Responsibility | Owns |
|---------|---------------|------|
| Scheduling | Availability, appointments, slots | slots, appointments |
| Clinical Records | HCE, diagnoses, prescriptions, PHI | clinical_records, patients |
| Billing | Invoices, payments, RIPS reports | invoices, payments |
| Notifications | Reminders, alerts, communication | notification_log |
| Identity | Auth, tenants, users, roles | tenants, users, roles |

---

## 4. User Roles and Personas

### 4.1 Roles

| Role | Scope | Description |
|------|-------|-------------|
| `TENANT_ADMIN` | Tenant | Clinic owner/administrator. Configures the clinic, manages users, sees all reports. |
| `PHYSICIAN` | Tenant | Treats patients. Full access to HCE for their own patients. Read-only to others within tenant. |
| `RECEPTIONIST` | Tenant | Manages scheduling and patient registration. No access to clinical notes. |
| `PATIENT` | Self | Accesses own appointments, records summary, and invoices via patient portal. |
| `PLATFORM_ADMIN` | Platform | ElevaForge internal. Manages tenant provisioning, billing, and platform health. |

### 4.2 Personas

**Dr. Valentina (PHYSICIAN):**
Independent cardiologist in Bogotá, sees 20 patients/day, currently uses paper records and
WhatsApp. Needs: fast appointment view, clinical notes with templates, prescription history.
Pain point: no-shows without reminders costing 3-5 appointments/week.

**Marcos (RECEPTIONIST):**
Manages front desk for a 3-physician clinic in Medellín. Needs: real-time availability across
all physicians, quick patient registration, collision-free scheduling.
Pain point: double bookings from two phones scheduling simultaneously.

**Ana (TENANT_ADMIN):**
Owner of a family medicine clinic. Needs: monthly revenue reports, RIPS export for EPS billing,
staff access management.
Pain point: billing department manually builds RIPS files in Excel — error-prone and hours of work.

**Carlos (PATIENT):**
Patient who wants to see his upcoming appointment, get a reminder, and download his lab results
summary without calling the clinic.
Pain point: calls the clinic 3 times before an appointment to confirm it exists.

---

## 5. Functional Requirements

### 5.1 Identity and Tenant Management

#### FR-ID-01 — Tenant Registration
The system shall allow a new clinic to self-register by providing: clinic legal name, NIT
(Colombia) or tax ID (international), primary contact email, country, and timezone.
On registration, the system shall:
- Create an isolated tenant schema in PostgreSQL
- Assign a TENANT_ADMIN role to the registering user
- Send an email verification before activating the tenant
- Generate a unique tenant slug for URL namespacing

#### FR-ID-02 — User Management
TENANT_ADMIN shall be able to invite users by email, assign roles, and deactivate accounts.
Deactivated users cannot log in but their audit records are retained permanently.

#### FR-ID-03 — Authentication
The system shall support:
- Email + password with Argon2id hashing
- OAuth2 via Google (for convenience in LATAM market)
- MFA via TOTP (required for PHYSICIAN and TENANT_ADMIN roles)
- Session tokens: JWT (15-minute access token) + refresh token (7 days, HttpOnly cookie)

#### FR-ID-04 — Session Security
- Concurrent session limit: 3 active sessions per user
- Session invalidation on password change
- Suspicious login detection: new country/device triggers email alert and step-up auth

### 5.2 Scheduling

#### FR-SCH-01 — Availability Configuration
PHYSICIAN or TENANT_ADMIN shall be able to configure:
- Working days and hours per physician
- Appointment slot duration (configurable per physician: 15, 20, 30, 45, 60 min)
- Blocked periods (vacations, breaks)
- Buffer time between appointments

#### FR-SCH-02 — Appointment Booking
RECEPTIONIST shall be able to book appointments by:
1. Selecting a physician
2. Viewing available slots in real time
3. Selecting a slot
4. Registering or selecting a patient
5. Confirming the booking

The system shall use **optimistic locking** on slots to prevent double-booking. If a conflict
is detected at commit time, the system shall surface a clear error with the next 3 available
slots as alternatives — not a generic "something went wrong" message.

#### FR-SCH-03 — Patient Self-Booking (Phase 2, not MVP)
Patients shall be able to book their own appointments via the patient portal in a future release.
The architecture must not preclude this — the availability API must be usable by both
receptionist and patient flows without code duplication.

#### FR-SCH-04 — Appointment Lifecycle
Appointments shall support the following states:
`PENDING → CONFIRMED → IN_PROGRESS → COMPLETED | CANCELLED | NO_SHOW`

State transitions shall emit Kafka events consumed by Notification and Billing services.

#### FR-SCH-05 — Conflict Resolution UX
When a double-booking conflict occurs, the response must include:
- The slot that was just taken
- The user/session that took it
- 3 nearest available alternatives
This is a domain requirement, not just a UI concern.

### 5.3 Clinical Records (HCE)

#### FR-CLN-01 — Patient Registration
Every patient record shall capture: full name, document type + number, date of birth, sex,
contact information, emergency contact, blood type, known allergies, and active medications.
All fields must support i18n validation (e.g., Colombian cedula vs. US SSN vs. EU formats).

#### FR-CLN-02 — Clinical Encounter
A PHYSICIAN shall be able to create a clinical encounter for a patient linked to an appointment,
containing: chief complaint, physical examination, diagnosis (ICD-10 coded), treatment plan,
prescriptions, and follow-up instructions.

The encounter must be **signed** (soft signature via authenticated action) and after signing,
the clinical content is **immutable**. Amendments are new versioned entries, never overwrites.
This is required by Colombian law (Ley 527/1999) and HIPAA Security Rule.

#### FR-CLN-03 — Prescription Management
Prescriptions shall include: medication name, dosage, frequency, duration, route, and
prescribing physician. The system shall warn (not block) if a new prescription conflicts with
a documented allergy or an active prescription of the same drug class.

#### FR-CLN-04 — PHI Audit Log
Every read, write, and export of patient PHI shall generate an immutable audit log entry
containing: timestamp, user ID, user role, patient ID, action type, source IP, and session ID.
This log is write-only from the application layer. No API exists to delete audit entries.
This directly fulfills Ley 1581/2012, HIPAA Security Rule §164.312(b), and GDPR Article 30.

#### FR-CLN-05 — Record Export
Patients (via portal) and TENANT_ADMIN shall be able to export a patient's complete HCE
as a structured PDF and as HL7 FHIR R4 JSON. This fulfills:
- GDPR Article 20 (right to data portability)
- Res. 1888/2025 (interoperabilidad HCE)
- Patient autonomy as a product value

#### FR-CLN-06 — Record Retention and GDPR Tension Resolution
Colombian law (MinSalud) requires HCE retention for a minimum of 15 years after the last
encounter. GDPR grants the right to erasure. The system resolves this conflict architecturally:

- **EU patients:** clinical data is pseudonymized (patient identity replaced with a UUID token,
  identity stored in a separate GDPR-governed store). On erasure request, the identity store
  is deleted. The clinical record is retained as pseudonymized data for legal retention.
- **Colombian patients:** full retention per MinSalud regulation. Erasure requests are
  documented and responded to with the legal basis for retention.
- This decision is documented in ADR-003 and surfaced to patients in plain language in the
  privacy policy.

### 5.4 Billing

#### FR-BIL-01 — Invoice Generation
The system shall generate invoices automatically when an appointment reaches COMPLETED status.
TENANT_ADMIN and the billing role shall be able to manually create, edit (before payment),
and void invoices.

#### FR-BIL-02 — RIPS Export
The system shall generate RIPS files in JSON format per Resolución 2275/2023. This is the
standard required for billing to EPS (Colombian health insurers). The export shall validate
the generated JSON against the official RIPS schema before delivery and report validation
errors with plain-language descriptions.

#### FR-BIL-03 — Payment Tracking
The system shall track payment status (PENDING, PAID, PARTIAL, OVERDUE) and generate
aging reports for TENANT_ADMIN. Integration with payment gateways (Stripe for international,
PSE/Nequi for Colombia) is Phase 2.

### 5.5 Notifications

#### FR-NOT-01 — Appointment Reminders
The system shall send reminders:
- 24 hours before appointment: email + WhatsApp (via Meta API)
- 2 hours before appointment: WhatsApp only
- Missed appointment: email to receptionist

Reminder content shall be localized based on patient's preferred language.

#### FR-NOT-02 — Notification Preferences
Patients and clinics shall be able to configure which notifications they receive and
via which channel. Opting out of clinical reminders shall be possible but shall require
explicit confirmation and be logged (GDPR consent management).

#### FR-NOT-03 — Delivery Reliability
Notifications shall be queued via Kafka. Failed deliveries shall be retried with exponential
backoff (3 retries: 5m, 15m, 1h). Permanent failures shall be logged and surfaced in the
admin dashboard as an operational alert.

### 5.6 Patient Portal

#### FR-PAT-01 — Patient Self-Service
Patients shall be able to:
- View upcoming and past appointments
- Receive and confirm appointments
- View their HCE summary (diagnoses, prescriptions, allergies)
- Download their complete HCE (PDF and FHIR JSON)
- Manage their notification preferences
- Submit a data export or deletion request (GDPR/Habeas Data)

#### FR-PAT-02 — Portal Authentication
The patient portal uses separate authentication from the clinic staff portal. Patients
authenticate with email + password or Google OAuth. MFA is optional for patients but
strongly encouraged via UX prompts.

### 5.7 Reporting

#### FR-REP-01 — Operational Reports
TENANT_ADMIN shall have access to:
- Daily/weekly/monthly appointment volume by physician
- No-show rate and cancellation rate
- Average appointment duration vs. configured slot duration
- Revenue by period, by physician, by service type

#### FR-REP-02 — Compliance Reports
The system shall generate:
- PHI access log export (for regulatory audit)
- RIPS summary for any date range
- Notification delivery report

---

## 6. Non-Functional Requirements

### 6.1 Performance

| Metric | Target | Rationale |
|--------|--------|-----------|
| Availability slot query response | < 300ms (p95) | Receptionist UX — feels instant |
| Appointment booking (with lock) | < 500ms (p95) | Acceptable for transactional operation |
| HCE page load (physician view) | < 1.5s (p95) | Critical for busy clinical workflow |
| Patient portal initial load | < 2s on 4G | LATAM connectivity reality |
| RIPS export (any date range) | < 10s | Acceptable for batch export |
| Notification dispatch (Kafka consumer) | < 30s from event to send | Reminder timeliness |

### 6.2 Reliability

- Platform uptime: 99.9% (excluding scheduled maintenance)
- Scheduled maintenance window: Sundays 2:00-4:00 AM local time, announced 48h in advance
- Data durability: PostgreSQL with daily automated backups, 30-day retention
- Appointment booking failures (due to conflict) shall never result in data loss —
  the slot is either booked or clearly not booked, never in an ambiguous state

### 6.3 Scalability

- Horizontal scaling for all services via stateless design (session state in Redis)
- Database connection pooling with PgBouncer
- Kafka partition strategy supports adding consumers without downtime
- Multi-tenancy uses schema-per-tenant for strict isolation with shared infrastructure

### 6.4 Accessibility

- Patient portal: WCAG 2.1 AA compliance
- Physician portal: keyboard-navigable critical flows (booking, record creation)
- Color contrast ratios meet AA standard for all severity indicators

### 6.5 Maintainability

Each bounded context is independently deployable. Adding a new notification channel requires
only: implementing a new adapter in the Notification Service, no changes to other services.
Every architectural decision has a corresponding ADR (Section 17).

---

## 7. Regulatory Compliance Framework

### 7.1 Colombia (Primary Market)

| Regulation | Requirement | CareLink Implementation |
|-----------|-------------|------------------------|
| Res. 3100/2019 | Estructura de HC electrónica con estándares definidos | HCE schema follows minsal structure; encounter signing per Ley 527/1999 |
| Res. 1888/2025 | Interoperabilidad: Resumen Digital de Atención (RDA) | FHIR R4 export endpoint per RDA spec |
| Res. 2275/2023 | RIPS en formato JSON | RIPS generator validates against official schema before export |
| Ley 1581/2012 | Habeas Data: consentimiento, acceso, rectificación, supresión | Consent collected at registration; data subject request workflow in patient portal |
| Ley 527/1999 | Validez de mensajes de datos y firma electrónica | Encounter signing via authenticated action with timestamp and user identity |

**Important scoping note:** CareLink is a **software tool** for healthcare providers. It is
not itself a healthcare provider and is not subject to REPS registration. The clinics using
CareLink are responsible for their own REPS registration. CareLink provides the technical
infrastructure to help them comply. This distinction is documented in Terms of Service.

### 7.2 International (Architecture Ready)

| Regulation | Scope | Implementation Strategy |
|-----------|-------|------------------------|
| GDPR (EU) | Any EU resident's data | Pseudonymization architecture (FR-CLN-06), consent management, 72h breach notification, DPO contact in privacy policy, data processing agreements |
| HIPAA (US) | US patient PHI | PHI audit log (FR-CLN-04), access controls, encryption at rest and in transit, BAA template for US clinic customers |

### 7.3 Data Retention Matrix

| Data Type | Colombia | EU (GDPR) | US (HIPAA) | Conflict Resolution |
|-----------|----------|-----------|------------|---------------------|
| HCE / Clinical records | 15 years (MinSalud) | Right to erasure | 6 years | Pseudonymization for EU (ADR-003) |
| Billing records | 5 years (DIAN) | 5 years (tax) | 7 years | Retain max, pseudonymize identity |
| Audit logs | Indefinite | Minimum needed | 6 years | Retain indefinitely, anonymize after 6 years |
| Notification logs | 1 year | 1 year | 1 year | Delete after 1 year |
| Session data | 7 days | 7 days | 7 days | Delete on expiry |

---

## 8. Security Architecture — OWASP 2025

### 8.1 Threat Model

Before any code is written, the following threat model must exist in `THREAT_MODEL.md`:

| Threat | Vector | Impact | Mitigation | Residual Risk |
|--------|--------|--------|-----------|---------------|
| Cross-tenant PHI access | IDOR via API | CRITICAL — regulatory + patient harm | Schema-per-tenant + API ownership validation | Very Low |
| PHI exfiltration via bulk export | Compromised TENANT_ADMIN account | HIGH | Rate limiting on exports, MFA required, export alerts | Low |
| Appointment double-booking | Race condition in concurrent booking | MEDIUM — operational | Optimistic locking with DB-level unique constraint | Very Low |
| Session hijacking | Token theft | HIGH | HttpOnly cookies, short-lived JWT, refresh rotation | Low |
| SQL injection via patient data fields | Malicious input in clinical notes | CRITICAL | Parameterized queries only, input validation | Very Low |
| Unauthorized PHI read via audit bypass | Compromised internal account | HIGH | Write-only audit log, separate audit DB user | Low |
| Notification spoofing | Forged reminders to patients | MEDIUM | Signed notification payloads, patient verification link |Low |

### 8.2 OWASP Top 10 2025 — Full Coverage

| OWASP 2025 | Category | CareLink Mitigation | Verification |
|------------|----------|---------------------|--------------|
| **A01** | Broken Access Control | Schema-per-tenant enforces isolation at DB level. Every API endpoint validates: (1) valid JWT, (2) tenant membership, (3) role permission, (4) resource ownership. SSRF: no user-supplied URLs are fetched server-side in MVP. | Test: cross-tenant patient access returns 403; cross-role clinical note access returns 403 |
| **A02** | Security Misconfiguration | Security headers on all responses (CSP, HSTS, X-Frame-Options, X-Content-Type-Options). Secrets in environment variables only. Spring Boot Actuator endpoints disabled in production. Startup validation fails fast if required env vars missing. | OWASP ZAP scan on staging; Checkov on Terraform |
| **A03** | Software Supply Chain | SCA via `mvn dependency-check` and `pip-audit` on every PR; Dependabot for Java, Python, and Node.js; SBOM generated per release. | SBOM attached to every GitHub Release |
| **A04** | Cryptographic Failures | TLS 1.3 minimum on all endpoints. PHI columns encrypted at rest with AES-256 (application-level, not just disk). Passwords: Argon2id. JWT: RS256 (asymmetric). No MD5/SHA1 (enforced by Semgrep rule). | Semgrep custom rule; nmap TLS scan |
| **A05** | Injection | Spring Data JPA (JPQL/criteria) — no native SQL string construction. FastAPI with Pydantic v2 validation. Input length limits on all fields. Semgrep rule banning string concatenation in query contexts. XSS: Next.js escapes by default; strict CSP. | Semgrep on every PR; SQLMap on staging API |
| **A06** | Insecure Design | THREAT_MODEL.md written before first commit. Fail-secure: appointment booking error = slot stays available, not booked in unknown state. Principle of least privilege: each service DB user has only SELECT/INSERT/UPDATE on its own schema. | Threat model peer-reviewed before each milestone |
| **A07** | Authentication Failures | MFA required for PHYSICIAN and TENANT_ADMIN. JWT access tokens: 15-minute expiry. Refresh tokens: 7-day expiry, rotated on use, invalidated on password change. Brute force: 5 failed logins → 15-minute lockout + email alert. Concurrent sessions: max 3 per user. | Test: 6th failed login returns 429 + lockout; expired JWT returns 401 |
| **A08** | Software/Data Integrity | Encounter signing: cryptographic timestamp + user identity bound to encounter at signing. Clinical records are immutable after signing (DB-level, not just application logic). Kafka message schema validation with Avro. | Test: attempt to modify signed encounter via API returns 409 |
| **A09** | Security Logging & Alerting | PHI audit log: immutable, write-only, separate DB user. Structured JSON logs for all API requests. Alerts: 10+ failed logins from same IP in 5 min → security event. Bulk PHI export → alert to TENANT_ADMIN. | Simulated attack traffic test; alert delivery test |
| **A10** | Mishandling of Exceptional Conditions | All service boundaries wrap external calls in typed exception handlers. Errors to client: generic message + request ID (no stack traces, no field names, no internal paths). Appointment booking timeout = slot released, operation marked FAILED. Global Spring exception handler. | Test: inject DB timeout, verify neutral response; test: invalid RIPS schema, verify descriptive (not technical) error |

### 8.3 Additional Security Requirements

**Race Conditions — Appointment Booking:**
Slot availability uses optimistic locking (`@Version` in JPA entity). The DB enforces a
unique constraint on `(physician_id, slot_start, status = BOOKED)`. Application-level
check + DB constraint = defense in depth. The constraint violation is caught, translated
to a domain exception, and surfaced with alternative slots.

**PHI at Rest:**
Clinical notes, diagnoses, and prescriptions are encrypted at the application level before
storage using AES-256-GCM. The encryption key is per-tenant, stored in a key management
service (Supabase Vault or equivalent), never in the database or application code.

**File Uploads:**
Lab results and document attachments: magic bytes validation, MIME type verification,
file size limit (10MB), metadata stripping (EXIF, document properties), ClamAV scan
before storage. Files stored in object storage (S3), never on the filesystem.

**GDPR Data Subject Requests:**
The patient portal includes a workflow for submitting access, portability, rectification,
and deletion requests. Each request generates a ticket visible to TENANT_ADMIN with a
30-day SLA counter. The system does not auto-delete PHI — a human (TENANT_ADMIN) must
confirm deletion after verifying the legal basis for or against it.

---

## 9. Technology Stack

| Layer | Technology | Version | Rationale |
|-------|-----------|---------|-----------|
| API Gateway / Identity | Spring Boot | 3.3.x | Mature security ecosystem, Spring Security for RBAC and OAuth2 |
| Scheduling Service | Spring Boot | 3.3.x | JPA optimistic locking, Spring's transactional guarantees |
| Clinical Records Service | Spring Boot | 3.3.x | Strong typing for PHI domain, JPA audit support |
| Billing Service | Spring Boot | 3.3.x | Consistency with other Java services, RIPS format complexity |
| Notification Service | Python FastAPI | 3.12 / 0.11x | Lightweight async consumer; Python ecosystem for WhatsApp/email libs |
| Event Bus | Apache Kafka | 3.7.x | Durable, ordered, replayable event log; supports audit trail |
| Physician Portal | Next.js | 14.x (App Router) | RSC for fast initial load; TypeScript type safety |
| Receptionist UI | Next.js | 14.x | Same codebase, different routes/layout |
| Patient Portal | Next.js | 14.x | Separate Next.js app — different auth domain |
| Styling | Tailwind CSS | 3.x | Rapid, consistent UI development |
| Primary DB (per service) | PostgreSQL | 16.x | ACID, schema-per-tenant, pgcrypto for PHI encryption |
| Session / Cache | Redis | 7.x | JWT refresh token store, slot availability cache |
| File Storage | Supabase Storage / MinIO | — | Document attachments, PHI export files |
| Schema Registry | Confluent Schema Registry | — | Avro schemas for Kafka message validation |
| Key Management | Supabase Vault | — | Per-tenant encryption keys for PHI at rest |
| Search | PostgreSQL FTS | — | Patient search within tenant (no Elasticsearch in MVP) |
| Infrastructure | Terraform | 1.7x | IaC for all cloud resources |
| CI/CD | GitHub Actions | — | Native runners — no Docker required |
| Containerization | **None** | — | Docker not used in any environment |
| Monitoring | Prometheus + Grafana Cloud | — | Service metrics and alerting |
| Tracing | OpenTelemetry + Grafana Tempo | — | Distributed tracing, no Docker required |
| Log Aggregation | Grafana Loki Cloud | — | Structured log search, managed |
| Testing — Java unit | JUnit 5 + Mockito | — | Pure unit tests, no infrastructure |
| Testing — Java integration DB | Zonky Embedded Database | — | Real PostgreSQL embedded, no Docker |
| Testing — Java integration Kafka | spring-kafka-test @EmbeddedKafka | — | In-memory Kafka broker, no Docker |
| Testing — Python | pytest + pytest-asyncio + WireMock | — | Unit + external API simulation |
| Contract Testing | Pact | — | Consumer-driven contracts between services |
| E2E Testing | Playwright | — | Critical user flows against staging |
| Load Testing | k6 | — | Native binary, no Docker |
| Security Scanning | Semgrep + pip-audit + OWASP ZAP | — | Native binaries in CI |
| i18n | i18next (frontend) + custom backend messages | — | EN, ES from day one |

---

## 10. Data Model

### 10.1 Multi-Tenancy Strategy

CareLink uses **schema-per-tenant** in PostgreSQL. Each tenant has its own schema
(e.g., `tenant_abc123`) with identical table structures. The `public` schema contains
only: tenant registry, platform users, and platform audit logs.

This strategy provides:
- Hard isolation: a misconfigured query cannot cross schema boundaries
- Independent backup and restore per tenant
- Compliance: tenant data can be fully deleted by dropping their schema
- Trade-off: documented in ADR-001 — complexity vs. isolation

### 10.2 Core Entities per Service

**Identity / Tenant Service (public schema)**
```
tenants: id, slug, legal_name, tax_id, country, timezone, status, plan, created_at
users: id, tenant_id, email, password_hash, role, mfa_enabled, mfa_secret_encrypted,
       status, last_login_at, failed_login_count, locked_until, created_at
sessions: id, user_id, refresh_token_hash, device_fingerprint, ip_address,
          expires_at, revoked_at
```

**Scheduling Service (tenant schema)**
```
physicians: id, user_id, specialty, license_number, appointment_duration_minutes
availability_blocks: id, physician_id, day_of_week, start_time, end_time, is_active
blocked_periods: id, physician_id, start_datetime, end_datetime, reason
appointments: id, physician_id, patient_id, slot_start, slot_end, status,
              version (optimistic lock), booked_by_user_id, created_at, updated_at
appointment_history: id, appointment_id, old_status, new_status, changed_by, changed_at
```

**Clinical Records Service (tenant schema)**
```
patients: id, document_type, document_number, full_name, date_of_birth, sex,
          phone, email, emergency_contact, blood_type, created_at
-- PHI fields (full_name, phone, email, emergency_contact) stored encrypted
allergies: id, patient_id, substance, reaction, severity, recorded_by, recorded_at
active_medications: id, patient_id, medication_name, dosage, frequency, prescribed_by
encounters: id, patient_id, appointment_id, physician_id, chief_complaint,
            physical_exam, diagnosis_icd10, treatment_plan, follow_up_instructions,
            signed_at, signed_by, version, created_at
-- All clinical text fields stored encrypted
prescriptions: id, encounter_id, medication, dosage, frequency, duration, route
phi_audit_log: id, user_id, role, patient_id, action, resource_type, resource_id,
               ip_address, session_id, timestamp
-- phi_audit_log is INSERT-only via DB trigger; no DELETE or UPDATE permission for app user
```

**Billing Service (tenant schema)**
```
invoices: id, patient_id, appointment_id, physician_id, items (JSONB),
          total_amount, currency, status, due_date, paid_at, created_at
payments: id, invoice_id, amount, method, reference, recorded_at
rips_exports: id, date_from, date_to, generated_at, generated_by, file_url, status
```

**Notification Service (tenant schema)**
```
notification_log: id, patient_id, appointment_id, channel, type, status,
                  scheduled_at, sent_at, retry_count, created_at
notification_preferences: id, patient_id, channel, type, opted_in, updated_at
consent_log: id, patient_id, consent_type, granted, granted_at, ip_address
```

### 10.3 Key Constraints

```sql
-- Optimistic locking on appointments (enforced in JPA @Version + DB)
ALTER TABLE appointments ADD CONSTRAINT uq_physician_slot_booked
  UNIQUE (physician_id, slot_start) WHERE status = 'BOOKED';

-- PHI audit log: app user has INSERT only, no UPDATE/DELETE
REVOKE UPDATE, DELETE ON phi_audit_log FROM app_clinical_user;

-- Encounter immutability after signing
CREATE OR REPLACE RULE no_update_signed_encounter AS
  ON UPDATE TO encounters
  WHERE OLD.signed_at IS NOT NULL
  DO INSTEAD NOTHING;
```

---

## 11. API Specification

### 11.1 Authentication

All endpoints except `/api/v1/auth/*` and `/api/v1/public/*` require:
`Authorization: Bearer <access_token>` (15-minute JWT, RS256)

Tenant context is derived from the JWT claim `tenant_id` — never from a request parameter.

### 11.2 Versioning

All endpoints are versioned under `/api/v1/`. When a breaking change is required, `/api/v2/`
is introduced while `/api/v1/` remains available for a documented sunset period (minimum 6 months).

### 11.3 Core Endpoints

**Identity**

| Method | Path | Role | Description |
|--------|------|------|-------------|
| POST | `/api/v1/auth/register` | Public | Tenant + admin user registration |
| POST | `/api/v1/auth/login` | Public | Email/password login → tokens |
| POST | `/api/v1/auth/refresh` | — (cookie) | Refresh access token |
| POST | `/api/v1/auth/logout` | Authenticated | Revoke refresh token |
| POST | `/api/v1/auth/mfa/setup` | Authenticated | Generate MFA TOTP secret |
| POST | `/api/v1/auth/mfa/verify` | Authenticated | Verify MFA code |
| GET/POST | `/api/v1/users` | TENANT_ADMIN | List / invite users |
| PATCH | `/api/v1/users/:id` | TENANT_ADMIN | Update role or deactivate |

**Scheduling**

| Method | Path | Role | Description |
|--------|------|------|-------------|
| GET | `/api/v1/physicians/:id/availability` | RECEPTIONIST+ | Available slots for date range |
| POST | `/api/v1/appointments` | RECEPTIONIST+ | Book appointment (with optimistic lock) |
| GET | `/api/v1/appointments` | RECEPTIONIST+ | List with filters |
| PATCH | `/api/v1/appointments/:id/status` | RECEPTIONIST+, PHYSICIAN | State transition |
| DELETE | `/api/v1/appointments/:id` | RECEPTIONIST+ | Cancel (soft delete) |

**Clinical Records**

| Method | Path | Role | Description |
|--------|------|------|-------------|
| POST | `/api/v1/patients` | RECEPTIONIST+ | Register patient |
| GET | `/api/v1/patients/:id` | PHYSICIAN, TENANT_ADMIN | Full patient record |
| POST | `/api/v1/patients/:id/encounters` | PHYSICIAN | Create encounter |
| POST | `/api/v1/patients/:id/encounters/:eid/sign` | PHYSICIAN | Sign (locks) encounter |
| GET | `/api/v1/patients/:id/export/pdf` | PHYSICIAN, PATIENT (own) | HCE PDF export |
| GET | `/api/v1/patients/:id/export/fhir` | PHYSICIAN, PATIENT (own) | FHIR R4 JSON export |

**Billing**

| Method | Path | Role | Description |
|--------|------|------|-------------|
| GET | `/api/v1/invoices` | TENANT_ADMIN | List with filters |
| POST | `/api/v1/invoices` | TENANT_ADMIN | Manual invoice creation |
| PATCH | `/api/v1/invoices/:id` | TENANT_ADMIN | Update (before payment only) |
| GET | `/api/v1/rips/export` | TENANT_ADMIN | Generate RIPS JSON for date range |

**Reports and Audit**

| Method | Path | Role | Description |
|--------|------|------|-------------|
| GET | `/api/v1/reports/operational` | TENANT_ADMIN | Appointment volume, no-shows |
| GET | `/api/v1/reports/revenue` | TENANT_ADMIN | Revenue by period/physician |
| GET | `/api/v1/audit/phi` | TENANT_ADMIN | PHI access log export (paginated) |

### 11.4 Standard Response Shapes

**Success:**
```json
{
  "data": { },
  "meta": { "request_id": "uuid", "timestamp": "ISO8601" }
}
```

**Error (never exposes internals):**
```json
{
  "error": {
    "code": "SLOT_ALREADY_BOOKED",
    "message": "The selected slot is no longer available.",
    "alternatives": ["2026-04-01T09:00", "2026-04-01T09:30", "2026-04-01T10:00"],
    "request_id": "uuid"
  }
}
```

---

## 12. Internationalization

### 12.1 Strategy

i18n is a first-class architectural concern, not a UI plugin:

- **Backend:** All user-facing messages are keys resolved via locale. Error messages,
  email templates, and report labels are never hardcoded in Spanish.
- **Frontend:** i18next with `next-intl`. Language detected from browser preference,
  overridable by user setting. Stored in user profile.
- **Data layer:** Dates stored as UTC timestamps. Displayed in tenant timezone.
  Currencies stored with ISO 4217 code, displayed with locale-appropriate formatting.
- **Phone numbers:** E.164 format in storage. Displayed per locale.
- **Document IDs:** Field type is locale-configurable (cedula, passport, NIT, SSN, etc.)

### 12.2 Supported Locales at Launch

| Locale | Language | Country | Regulatory Profile |
|--------|----------|---------|-------------------|
| `es-CO` | Spanish | Colombia | Res. 3100, Ley 1581, RIPS |
| `en-US` | English | USA | HIPAA (architecture ready) |
| `en-GB` | English | UK | GDPR (architecture ready) |

### 12.3 Content That Must Be Localized

- UI labels and navigation
- Error messages surfaced to users
- Email and WhatsApp notification templates
- PDF report headers and labels
- Consent and privacy policy text
- Date, time, currency, phone number formats
- Regulatory disclaimer text per jurisdiction

---

## 13. Testing Strategy

### 13.1 Philosophy

Testing is not a phase — it runs in parallel with every development task. The test suite
is the primary documentation of system behavior. A feature is not done until it has tests
that would catch a regression.

### 13.2 Test Layers

**Unit Tests (per service)**
- Target: domain logic, use cases, value objects
- Tools: JUnit 5 + Mockito (Java), pytest + pytest-asyncio (Python)
- Coverage target: 80% line coverage on domain and application layers
- What is NOT unit tested: controllers, repositories, configuration classes

**Integration Tests — Java (no Docker)**
- Target: service + real database, service + Kafka
- PostgreSQL: Zonky Embedded Database (`@AutoConfigureEmbeddedDatabase`) — real PostgreSQL
  engine embedded in the test JVM, no Docker, works natively on Windows
- Kafka: `@EmbeddedKafka` from `spring-kafka-test` — in-memory Kafka broker, no Docker
- Key scenarios: optimistic lock conflict, PHI audit log insert, schema isolation
- Trade-off (documented in ADR-007): Zonky does not replicate schema-per-tenant provisioning
  exactly as in production. Mitigated by smoke test against real Supabase in staging.

**Contract Tests**
- Target: API contracts between services and between frontend and backend
- Tool: Pact (consumer-driven contract testing)
- Every Kafka message schema has a Pact contract
- Every REST endpoint consumed by the frontend has a Pact contract

**Security Tests**
- Cross-tenant access: automated test attempting to access another tenant's patient
  → must return 403
- Role boundary tests: RECEPTIONIST attempting to read clinical notes → 403
- Optimistic lock conflict: two concurrent booking requests for same slot → one succeeds,
  one receives SLOT_ALREADY_BOOKED with alternatives
- Immutability: attempt to update a signed encounter → 409
- PHI audit: every read of patient data generates exactly one audit log entry
- MFA bypass: attempt to authenticate without MFA for PHYSICIAN role → 401

**E2E Tests (Playwright)**
- Complete appointment booking flow (receptionist)
- Clinical encounter creation and signing (physician)
- RIPS export for a date range (admin)
- Patient portal: view appointment, download HCE
- Patient data subject request submission

**Load Tests (k6)**
- Slot availability query: 100 concurrent receptionists querying a physician's calendar
- Concurrent booking: 10 concurrent attempts to book the same slot → exactly 1 succeeds
- Results documented in `docs/load-test-results.md` with p95 and p99 latencies

**Accessibility Tests**
- Patient portal: axe-core automated scan on every Playwright run
- Manual keyboard navigation test for appointment booking and HCE view

### 13.3 CI Pipeline Quality Gates

Every PR must pass before merge:
- All unit and integration tests pass
- Pact contracts verified
- Semgrep (SAST) — zero new issues at HIGH or CRITICAL
- SCA (mvn dependency-check / pip-audit) — zero new CRITICAL vulnerabilities
- Gitleaks — zero secrets detected
- Ruff (Python) + Checkstyle (Java) + ESLint (TypeScript) — zero violations
- Coverage does not decrease from main branch baseline

---

## 14. Observability

### 14.1 Structured Logging

Every service emits structured JSON logs with:
```json
{
  "timestamp": "ISO8601",
  "level": "INFO|WARN|ERROR",
  "service": "scheduling-service",
  "trace_id": "opentelemetry-trace-id",
  "span_id": "opentelemetry-span-id",
  "tenant_id": "hashed",
  "user_id": "hashed",
  "event": "appointment.booked",
  "duration_ms": 234,
  "request_id": "uuid"
}
```

Never logged: PHI content, passwords, tokens, encryption keys, full patient names, diagnoses.
Audit log for PHI access is separate from operational logs and is in its own append-only table.

### 14.2 Metrics (Prometheus)

| Metric | Type | Labels |
|--------|------|--------|
| `carelink_appointments_total` | Counter | tenant_id (hashed), status, physician_specialty |
| `carelink_booking_conflicts_total` | Counter | tenant_id (hashed) |
| `carelink_api_request_duration_seconds` | Histogram | service, path, method, status_code |
| `carelink_notification_delivery_total` | Counter | channel, status |
| `carelink_phi_accesses_total` | Counter | role, action |
| `carelink_rips_export_duration_seconds` | Histogram | — |

### 14.3 Distributed Tracing

OpenTelemetry instrumentation on all services. Trace context propagated via HTTP headers
and Kafka message headers. Jaeger UI for trace visualization.

### 14.4 Alerting Rules

| Rule | Condition | Severity |
|------|-----------|----------|
| High booking conflict rate | > 5% of bookings end in conflict in 10-min window | WARNING — possible UX or slot config issue |
| Auth failure spike | 10+ failed logins from same IP in 5 min | CRITICAL — potential attack |
| Notification failure | Delivery failure rate > 10% in 15-min window | WARNING |
| Service latency degradation | p95 > 2x baseline for 5 minutes | WARNING |
| PHI bulk export | Any export > 1000 records in single request | ALERT to TENANT_ADMIN |

---

## 15. Deployment Architecture

### 15.1 Infrastructure

**No Docker is used in any environment — local, CI, or production.**
Services deploy as native JARs and Python processes via platform-as-a-service.

| Component | Platform | Notes |
|-----------|----------|-------|
| Spring Boot services | Railway | Deploy from JAR via Nixpacks — auto-detects Maven, no Dockerfile |
| FastAPI Notification | Railway | Deploy from `requirements.txt` — auto-detected, no Dockerfile |
| Next.js Physician Portal | Vercel | Zero-config deployment, Edge CDN |
| Next.js Patient Portal | Vercel | Separate app, separate deployment |
| PostgreSQL | Supabase | Managed PostgreSQL 16, schema-per-tenant via Supabase API |
| Redis | Upstash | Serverless Redis, free tier for dev/staging, pay-per-use in prod |
| Kafka | Confluent Cloud | Managed Kafka, free tier (10GB/month), KRaft mode — no Zookeeper |
| File Storage | Supabase Storage | PHI files with per-tenant bucket policies, SSE enabled |
| Key Management | Supabase Vault | Per-tenant encryption keys for PHI at rest |
| DNS + CDN | Vercel Edge Network | TLS termination, global CDN |
| Secrets | Railway + Vercel env vars | Never in code or `.env` committed to git |
| IaC | Terraform | Provisions Supabase project, Confluent cluster, Upstash, Railway apps |
| Monitoring | Grafana Cloud (free tier) | Prometheus metrics, Loki logs, Tempo traces |
| Tracing | OpenTelemetry → Grafana Tempo | No self-hosted infrastructure required |

### 15.2 Environments

| Environment | Purpose | Infrastructure | Data Policy |
|-------------|---------|---------------|-------------|
| Local | Developer machines | PostgreSQL native + Upstash Redis + Confluent Cloud free | Synthetic data only, never real PHI |
| CI | Automated tests | Zonky embedded DB + @EmbeddedKafka — no external services | Ephemeral, no persistence |
| Staging | Pre-production validation | Supabase + Upstash + Confluent (shared with dev) | Anonymized snapshots, ZAP DAST runs here |
| Production | Live system | Supabase + Upstash + Confluent (dedicated projects) | Real PHI, full compliance |

**Local setup (Windows) — no Docker required:**
```
PostgreSQL 16  → native installer: postgresql.org/download/windows
Redis          → Upstash free tier (cloud) — no Windows binary available natively
Kafka          → Confluent Cloud free tier — no local broker needed
Java services  → ./mvnw spring-boot:run
Python service → uvicorn app.main:app --reload --port 8084
Next.js        → npm run dev
```

### 15.3 CI/CD Pipeline — No Docker

```
PR opened →
  1. Semgrep (SAST)              → fail on HIGH/CRITICAL — native binary
  2. pip-audit + cyclonedx-py    → SCA for Python deps — no Docker
  3. mvn dependency-check        → SCA for Java deps — no Docker
  4. Gitleaks                    → fail on any secret — native binary
  5. Unit tests (Java + Python)  → fail on any failure
  6. Integration tests Java      → Zonky embedded DB + @EmbeddedKafka — no Docker
  7. Integration tests Python    → pytest + WireMock — no Docker
  8. Pact contract verify        → fail if contracts broken
  9. Coverage check              → fail if decreased from baseline
 10. Lint (Checkstyle + ruff + ESLint) → fail on any violation
 11. Vercel preview deployment   → Next.js preview URL generated automatically
 12. OWASP ZAP (DAST)           → runs against Vercel preview URL — native binary

Merge to main →
 13. All above +
 14. Deploy Spring Boot JARs to Railway (staging) via Railway CLI
 15. Deploy FastAPI to Railway (staging)
 16. Smoke tests on staging (curl health endpoints)
 17. Playwright E2E on staging
 18. Manual approval gate → Deploy to production Railway + Vercel
```

---

## 16. MVP Scope

### 16.1 In Scope

- Tenant registration and multi-tenant isolation
- User management: TENANT_ADMIN, PHYSICIAN, RECEPTIONIST, PATIENT roles
- Email + Google OAuth authentication with MFA for privileged roles
- Physician availability configuration and real-time slot querying
- Appointment booking with optimistic locking and conflict resolution UX
- Patient registration with encrypted PHI fields
- Clinical encounter creation and signing (immutable after signing)
- Prescription management with allergy/duplication warnings
- PHI audit log (immutable, write-only)
- Basic invoice generation on appointment completion
- RIPS export in JSON format per Res. 2275/2023
- Appointment reminder notifications via email and WhatsApp
- Patient portal: view appointments, download HCE summary
- Operational reports: appointment volume, no-show rate
- GDPR data subject request submission workflow
- Full OWASP 2025 Top 10 mitigations documented and tested
- i18n: ES-CO and EN-US at launch
- Observability: Prometheus metrics, structured logs, distributed tracing

### 16.2 Out of Scope — Future Releases

- Patient self-booking via portal (architecture allows it, not built in MVP)
- Payment gateway integration — Stripe/PSE (Phase 2)
- Telemedicine video integration (Phase 2)
- Lab results integration with external labs (Phase 3)
- Native mobile apps — iOS/Android (Phase 3)
- AI-assisted diagnosis suggestions (explicitly deferred, requires separate regulatory review)
- Multi-location support within one tenant (Phase 2)

---

## 17. Architectural Decisions (ADRs)

### ADR-001 — Schema-per-Tenant vs. Row-level Multi-tenancy

**Decision:** Schema-per-tenant
**Context:** PHI isolation is a hard regulatory requirement. A misconfigured WHERE clause
in row-level tenancy can expose data across tenants — this is a catastrophic compliance failure.
**Rationale:** Schema-per-tenant makes cross-tenant access impossible at the database level.
The application cannot construct a query that crosses schemas without explicit schema-switching,
which is never done in application code.
**Trade-offs:** Higher operational complexity (migrations must run per tenant), more DB
connections. Accepted because isolation outweighs complexity at this stage.
**Review trigger:** If tenant count exceeds 500, evaluate RLS as a complement.

### ADR-002 — Hexagonal Architecture per Service

**Decision:** Hexagonal (Ports and Adapters) within each bounded context
**Context:** The domain logic (scheduling rules, clinical record invariants, billing rules)
must be testable independently of the database, Kafka, and HTTP layer.
**Rationale:** Ports define what the domain needs. Adapters implement those ports for
specific technologies. Swapping PostgreSQL for another DB, or Kafka for RabbitMQ, touches
only adapters — the domain is unchanged.
**Trade-offs:** More initial boilerplate than a layered architecture. Accepted because
the domain is complex and long-lived.

### ADR-003 — GDPR Erasure vs. Medical Record Retention

**Decision:** Pseudonymization for EU patients
**Context:** Colombian MinSalud requires 15-year HCE retention. GDPR Article 17 grants
the right to erasure. These are directly conflicting obligations.
**Rationale:** Pseudonymization satisfies GDPR's erasure right (the data subject can no
longer be identified) while preserving the clinical record for regulatory retention.
The identity-clinical record link is broken; the clinical record becomes anonymous.
**Implementation:** Identity stored in a GDPR-governed store (erasable). Clinical record
stores only a pseudonymous UUID. On erasure: identity store deleted, clinical record retained.
**Legal basis:** GDPR Recital 156 — processing for archiving purposes in the public interest
(clinical records) may override erasure rights.

### ADR-004 — Optimistic Locking for Slot Booking

**Decision:** Optimistic locking (JPA @Version) + DB unique constraint
**Context:** Two receptionists can simultaneously attempt to book the same slot. A single
application-level check is insufficient — the window between check and write allows both to succeed.
**Rationale:** Two-layer defense: JPA @Version catches conflicts at the ORM level.
The DB unique constraint on `(physician_id, slot_start) WHERE status = BOOKED` is the
final safety net — the DB will reject even a conflict that bypassed the ORM.
**UX requirement:** Conflict response must include 3 alternative slots — not a generic error.

### ADR-005 — Microservices Pragmatic Split

**Decision:** 4 domain services (not one per entity, not one monolith)
**Context:** The system has 5 bounded contexts with different scaling and availability
requirements. A monolith would tightly couple scheduling availability (high-frequency reads)
with HCE writes (compliance-sensitive, low-frequency). Full microservices per entity would
create unnecessary network overhead and operational complexity.
**Rationale:** Services split along domain boundaries, not technical boundaries.
Scheduling, Clinical Records, Billing, and Notifications have genuinely different scaling
profiles and team ownership boundaries.
**Review trigger:** If any service grows beyond 50,000 lines of domain code, evaluate split.

### ADR-007 — No Docker in Any Environment

**Decision:** Zero Docker usage — local development, CI/CD, and production all use
native processes and platform-as-a-service.
**Context:** Docker causes performance degradation and service conflicts on the development
machine, and the team policy prohibits its use across all environments.
**Rationale:**
- Local: PostgreSQL installed natively on Windows. Redis and Kafka via cloud free tiers
  (Upstash, Confluent Cloud) — no local broker needed.
- Testing: Zonky Embedded Database replaces Testcontainers for PostgreSQL integration tests.
  `@EmbeddedKafka` replaces Testcontainers for Kafka integration tests. Both work natively
  on Windows with no external dependencies.
- CI/CD: GitHub Actions native runners with `setup-java` and `setup-python`. No image builds.
  SCA via `pip-audit` and `mvn dependency-check` instead of Trivy (which requires Docker daemon).
- Production: Railway deploys Spring Boot JARs and Python processes via Nixpacks (no Dockerfile
  required). Vercel deploys Next.js. All managed services (Supabase, Upstash, Confluent Cloud).
**Trade-offs:**
- Zonky does not replicate schema-per-tenant provisioning exactly as in production.
  Mitigated by a smoke test in staging (real Supabase) that validates schema provisioning
  on every merge to main.
- Confluent Cloud free tier (10GB/month) may require upgrade for high-volume staging tests.
- Railway free tier has sleep-on-idle behavior — upgrade required before production launch.
**Review trigger:** If the team adopts Docker in the future, Testcontainers replaces
Zonky + @EmbeddedKafka and a container orchestration platform (e.g., ECS) replaces Railway.

---

## 18. Acceptance Criteria

| ID | Criterion | Verification |
|----|-----------|-------------|
| AC-01 | A new tenant can register and a receptionist can book an appointment within 10 minutes of signup | Timed manual walkthrough |
| AC-02 | Two simultaneous booking requests for the same slot result in exactly one success and one SLOT_ALREADY_BOOKED response with alternatives | Automated concurrency test (k6) |
| AC-03 | A RECEPTIONIST cannot read clinical encounter content — returns 403 | Automated role boundary test |
| AC-04 | A signed encounter cannot be modified — returns 409 | Automated test: PUT on signed encounter |
| AC-05 | Every PHI read generates exactly one entry in phi_audit_log | Automated test: read patient, verify audit log count |
| AC-06 | Cross-tenant patient access returns 403 (not 404, not 200) | Automated cross-tenant test |
| AC-07 | RIPS export validates against official schema before delivery | Automated test with known-valid and known-invalid data |
| AC-08 | Appointment reminder sent within 30 seconds of trigger event | Integration test with Kafka consumer timing |
| AC-09 | Patient can download their HCE as FHIR R4 JSON from the patient portal | Playwright E2E test |
| AC-10 | OWASP ZAP finds zero HIGH or CRITICAL issues on the staging deployment | ZAP scan report attached to release |
| AC-11 | Load test: 100 concurrent slot queries complete in < 300ms p95 | k6 report in docs/load-test-results.md |
| AC-12 | UI renders correctly in ES-CO and EN-US locales with no untranslated keys | Playwright test with locale switching |

---

## 19. Glossary

| Term | Definition |
|------|-----------|
| **Bounded Context** | A domain area with its own model and rules, isolated from other contexts. Each CareLink service is a bounded context. |
| **Optimistic Locking** | A concurrency strategy where a transaction reads data with a version number and fails if the version has changed at commit time, instead of locking the row upfront. |
| **PHI** | Protected Health Information — any data that can identify a patient combined with health information. Regulated by HIPAA (US) and health data laws globally. |
| **Pseudonymization** | Replacing identifying data with a pseudonym (e.g., UUID) so the record can no longer be directly attributed to an individual without additional information stored separately. |
| **Schema-per-tenant** | Multi-tenancy strategy where each customer (clinic) has their own database schema, providing hard isolation at the DB level. |
| **Hexagonal Architecture** | Architecture pattern where domain logic is at the center, and all external dependencies (DB, APIs, message queues) are adapters connecting to domain ports. |
| **RIPS** | Registro Individual de Prestación de Servicios — Colombian standard for billing health services to insurers (EPS). Defined in JSON format by Res. 2275/2023. |
| **FHIR R4** | Fast Healthcare Interoperability Resources version 4 — international standard for clinical data exchange. Required by Res. 1888/2025 in Colombia. |
| **Idempotency** | Property of an operation where executing it multiple times produces the same result as executing it once. Critical for retry logic in distributed systems. |
| **ADR** | Architecture Decision Record — document capturing the context, decision, and trade-offs of a significant architectural choice. |
| **PHI Audit Log** | Immutable record of every access to patient health information. Required by HIPAA, Ley 1581, and GDPR Article 30. |
| **EPS** | Entidad Promotora de Salud — Colombian health insurer. Clinics bill EPS using RIPS format. |
| **IPS** | Institución Prestadora de Servicios de Salud — healthcare provider entity in Colombia. |
