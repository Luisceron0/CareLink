# CareLink

A reference implementation of a multi-tenant clinical platform, with the clinical
domain of a Colombian public hospital (ESE) built on top of it: clinical records,
triage, NANDA/NIC/NOC nursing diary, a k-anonymity knowledge engine, interconsultations,
lab, and pharmacy.

> **Not a product.** No real patient health information, no clinical operation, no
> production environment — by design, not by lack of time. See [SRS §1.6](docs/SRS.md)
> for the verifiable version of that claim: boot fails outside demo mode and against a
> database that isn't stamped as synthetic-data-only.

## Run it locally

Requires Docker.

```bash
cp .env.example .env       # fill in REFRESH_TOKEN_HMAC_SECRET, POSTGRES_PASSWORD,
                            # CARELINK_APP_DB_PASSWORD, and CLINIC_ENCRYPTION_KEY
                            # (openssl rand -base64 32 for the last two)
docker compose up
```

This starts the backend (`:8080`), PostgreSQL 16 (`:5432`), the frontend (`:5173`), and
Mailpit (`:8025`, captures verification/invitation emails instead of sending them for
real). Backend health: `http://localhost:8080/actuator/health`.

All four services expose a `healthcheck` — `docker compose ps` shows `healthy` once the
stack is actually up, not just once the process is running.

**Demo mode is not a flag you can turn off.** `DemoModeGuard` fails startup if
`DEMO_MODE` isn't `true`, if `APP_ENV` looks like production, or if the database isn't
stamped `SYNTHETIC_DATA_ONLY`. That's a mechanical constraint, not a README promise —
see the test that proves it in `ContainmentGuardIT`.

Tests (requires JDK 21 on the host — `docker compose` doesn't, the image bundles it):

```bash
export JAVA_HOME=/path/to/your/jdk-21
./mvnw -f services/identity-service/pom.xml verify   # unit + integration, real embedded PostgreSQL
```

For a guided tour of the running app — role isolation, a signed encounter rejecting an
edit with 409, both sides of the knowledge engine's k-anonymity, a specialist's JWT
going from 200 to 403 the moment an interconsultation closes — see
[docs/portfolio/SCREENSHOTS.md](docs/portfolio/SCREENSHOTS.md), captured against the
real stack. Script for the full video walkthrough:
[docs/portfolio/WALKTHROUGH.md](docs/portfolio/WALKTHROUGH.md).

## Security — threat model & audits

The full STRIDE threat model, controls, and verifiable acceptance criteria live in
[SRS §8](docs/SRS.md) and §18. The ones that shaped the design:

- Tenant isolation via schema-per-tenant, plus a `service_id` filter within the
  tenant — enforced in the query's `WHERE`/`HAVING`, never on rows already fetched.
- A specialist's interconsultation access is **re-validated on every request** — never
  cached — verified over HTTP with the same JWT going from 200 to 403 the moment the
  interconsultation closes, no re-login.
- The knowledge engine suppresses results with `COUNT(DISTINCT patient_id) < 5`,
  inside the query — rows below the threshold never leave the database.
- `audit_log` and signed clinical records are immutable via PostgreSQL trigger, for
  any role that connects, including the administrator — not just application logic.
- PHI encrypted at rest with AES-256-GCM, random IV per operation, key derived
  per tenant.
- The application **refuses to boot** without its secrets configured — see the real
  finding below where that failed and how it got fixed.

**Two independent audits, both against the running stack, not a tabletop exercise:**

| Audit | What it covered | Result |
|---|---|---|
| [2026-08-06](docs/security/AUDIT-2026-08-06.md) | SAST (self-written semgrep rules, validated against deliberately vulnerable code before being trusted), `sqlmap --level 3`, secret sweep | 1 critical finding (a default-valued HMAC secret, live in the repo) — fixed, with evidence the gate meant to catch it didn't |
| [2026-08-07](docs/security/AUDIT-2026-08-07.md) | Manual adversarial pass — IDOR, role bypass, JWT, rate limiting — plus repo cleanup | 1 high finding (`AUDITOR` role could read full PHI instead of audit-log-only) — fixed |

See [SECURITY.md](SECURITY.md) for the full map of where each control and report lives.

## What's built

**All nine sub-phases of Milestone 1 are closed.** Each with its acceptance criteria
verified by integration test *and* against the real running stack — not just green
tests (see why that distinction matters in [tasks/lessons.md](tasks/lessons.md)).

| Module | Phase |
|---|---|
| Containment (`DemoModeGuard`) + append-only audit log, PostgreSQL with two roles | Phase 1 |
| Identity: tenants, role-based user invitation, RS256/JWKS auth, Argon2id | Phase 2 |
| Patient + clinical encounter, signed and immutable at the database trigger level | Phase 2 |
| Admissions + Manchester triage | Phase 3 |
| Nursing diary (NANDA/NIC/NOC) + k-anonymity knowledge engine | Phase 4 |
| Interconsultations, with specialist access re-validated on every request | Phase 5 |
| Lab + pharmacy (adherence tracking, conflicts that warn without blocking) | Phase 6 |
| Frontend: React 18 + Vite SPA, role-scoped views, verified in a real browser | Phase 7 |
| End-to-end security audit: sqlmap, self-written SAST, 1 high finding fixed | Phase 8 |

The phase-by-phase plan is in [tasks/todo.md](tasks/todo.md), with evidence for each
acceptance criterion. Every phase ends in something that runs: if the work had stopped
partway through, what was left was a working demo of everything built so far, not eight
half-finished modules.

## Project structure

- `services/identity-service/` — the backend. Contains **both** bounded contexts:
  `identity/` (tenants, users, sessions) and `clinical/` (PHI: patients, encounters,
  diary, lab, pharmacy, interconsultations). Two contexts inside one service, not eight
  microservices — see [SRS §3.3](docs/SRS.md).
- `frontend/` — React 18 + Vite + Tailwind SPA, role-scoped views (ADR-014).
- `docs/SRS.md` — the project's single source of truth. One file, no mirrors:
  requirements, threat model (§8), ADRs (§17), and acceptance criteria with their real
  status (§18) all live there, not scattered across documents that could drift apart.
- `docs/adr/` — standalone architecture decision records, including superseded ones.
- `docs/security/` — audit reports, with findings, severity, and evidence.
- `docs/portfolio/` — walkthrough script for the recording.
- `services/identity-service/src/main/resources/db/migration/` — Flyway migrations.
- `tasks/` — the active plan (`todo.md`, one task = one commit) and lessons learned
  (`lessons.md`) — the real defects found while building, with root cause, not just
  the fix.

## License

MIT — see [LICENSE](LICENSE).
