# Plan activo: Milestone 1 (unificado) — CareLink + dominio clínico ClinicTrack ESE
**Inicio:** 2026-08-03
**Objetivo:** un sistema multi-tenant con Identity endurecido y el dominio clínico completo
de ClinicTrack (Patient → Encounter → Triage → Diary/Knowledge Engine → Interconsultations
→ Labs/Pharmacy) construido como 9 sub-fases verticales, cada una demostrable por sí sola.

**Nota de alcance (ADR-013 adenda):** este milestone es más grande de lo que Arch-Sentinel
recomendó — decisión del autor, respetada, disidencia documentada en
`docs/adr/ADR-013-fusion-carelink-clinictrack.md`. La estructura en sub-fases existe para
que un corte de tiempo en cualquier punto deje algo demostrable, no un sistema a medio
construir en 8 frentes a la vez.

## Sub-fase 0: Higiene de repositorio (bloquea todo)
- [x] Eliminar `services/api-gateway-identity/` — ADR-010
      Evidencia: `grep -rn "HS256\|dev-secret" services/` sin coincidencias. Se
      eliminaron también los wrappers de raíz que solo servían a ese servicio
      (`requirements.txt`, `run-identity-tests.sh`, `run-identity-dev-checks.sh`).
      **AC-04 no cerrado todavía:** su verificación es un gate de CI, y ese gate es
      tarea de Sub-fase 1. Hoy la condición se cumple, pero nada la hace bloqueante
      (lección del `|| true`: no dar por existente un gate que no existe).
- [x] `git rm --cached test_identity.db`; `*.db` a `.gitignore`
      **AC-03 PASS:** `git ls-files '*.db'` vacío. El archivo sigue en disco, ignorado.
- [ ] **Decidir `git filter-repo` para purgar `test_identity.db` del historial**
      Única tarea abierta de la sub-fase. Requiere decisión explícita del autor: el PR #1
      ya está mergeado, así que reescribir historia invalida cualquier clon o fork
      existente y obliga a force-push. Hasta que se decida, el archivo con hashes
      Argon2id de prueba sigue siendo recuperable de commits anteriores.
- [x] Consolidar a un solo `docs/SRS.md` v3.0; archivar `carelink-srs.md` y el SRS de
      ClinicTrack como input histórico (no se borran, se mueven a `docs/archive/`)
      Archivados `carelink-srs-v1.0.md` (1157 líneas) y `PROJECT_PLAN-v1.0.md`, que era
      un plan paralelo a este archivo. **El SRS de ClinicTrack nunca estuvo en el repo**
      — no hay nada que archivar de ese lado.
- [x] `docker-compose.yml`: backend + PostgreSQL 16 + frontend (placeholder hasta
      Sub-fase 7) — ADR-012
      Verificado levantando el stack, no por inspección del archivo:
      `backend healthy {"status":"UP"} HTTP 200` · `db healthy` · `frontend HTTP 200`.
      Levantarlo destapó cuatro defectos que ningún test detectaba: el puerto
      `VerificationTokenRepository` sin adaptador (el contexto de Spring no cargaba),
      `spring-boot-starter-actuator` ausente pero configurado, `SecurityConfig`
      devolviendo 403 en `/actuator/health`, y `MailHealthIndicator` marcando el
      servicio DOWN contra un SMTP que §16.4 decide no construir.
- [x] Confirmar o cambiar nombre del sistema (SRS §20, item abierto)
      **Se mantiene CareLink.** El paquete Java es `com.carelink`, el SRS lo arrastra
      como supuesto de trabajo y renombrar toca 800+ líneas de documentación por cero
      ganancia técnica. Ítem cerrado, sale de "abiertos" del SRS §20.

### Descubierto durante la Sub-fase 0 (no estaba en el plan original)
- [x] **El reactor Maven estaba roto.** `scheduling-service`, `clinical-service` y
      `billing-service` declaraban `<parent>` sin `<relativePath>`; Maven no resolvía el
      POM padre y `./mvnw test` fallaba en la raíz. CI lo ocultaba con `|| true`.
      Los tres módulos eliminados — además de romper el build contradecían §3.3
      (clinical vive *dentro* de identity-service) y §16.3.
      **Verificado desde la raíz:** `./mvnw -B test` -> 23 tests, BUILD SUCCESS.
- [x] Borrado `portals/` — dos apps Next.js (patient + physician) que ADR-014 descarta
      explícitamente a favor de una SPA única sin Next.js.
- [x] Borrado `services/notification-service/` — FastAPI de 6 líneas; §9 excluye un
      segundo runtime backend y §16.3 no construye Notifications.
- [x] Borrados `scripts/prepare_commit.sh` y `COMMIT_MESSAGE.txt`.
- [x] `pom.xml` con un solo módulo; `ci.yml` sin los 3 pasos muertos ni el `|| true`
      del build de Java — la máscara se fue junto con lo que ocultaba.
- [x] `README.md` reescrito — describía 5 microservicios, portales Next.js y apuntaba a
      `carelink-srs.md`, ya archivado.
- [x] **`identity-service` nunca había arrancado.** `VerificationTokenRepository` era un
      puerto sin adaptador (7 de 8 puertos implementados, ese en 0) y el contexto de
      Spring no cargaba. Ningún test usaba `@SpringBootTest`, así que 22 tests verdes
      convivían con una aplicación que no iniciaba. Adaptador JPA escrito +
      `ApplicationContextLoadsTest`, verificado rompiéndolo a propósito.
- [ ] **CI no ejecuta los tests de Java.** El paso es `mvn -DskipTests package`. El SRS
      §15.3 lista "Unit + integration tests (Java) — fail on any failure" como paso 5 del
      pipeline, y no existe. Se corrige en Sub-fase 1, junto con los otros gates de CI
      (AC-03, AC-04) — no acá, para no adelantar trabajo de otra sub-fase.
- [ ] **Tokens de verificación sin expiración.** El puerto `VerificationTokenRepository`
      no contempla vencimiento; un token es válido para siempre. Agregarlo cambia la
      semántica del contrato, así que va a los gaps de Identity de Sub-fase 2.
- [x] Registro de ADRs: `ADR-008` estaba duplicado (el de infra Railway/Supabase colisionaba
      con el de GDPR/retención del SRS §17) y `ADR-00X-jwt-management` no tenía número.
      Renumerados a ADR-016 (marcado superado por ADR-012/ADR-015) y ADR-017. Añadidos
      al SRS §17.
- [x] **Entorno:** el JDK local era 11 y el SRS exige 21 — nada compilaba. Instalado
      OpenJDK 21. Línea base verificada: 22 tests verdes en `identity-service`.

## Sub-fase 1: Contención + Audit Log (todo lo demás depende de esto)
- [ ] **Arreglar `migrations/` antes de agregarle nada** (descubierto en Sub-fase 0):
      `0001_create_identity.sql` y `001_public_schema.sql` crean `tenants` y `users` con
      formas incompatibles (`legal_name`/`contact_email` vs. `name`/`role`/`created_at`),
      y como ambas usan `IF NOT EXISTS`, la segunda se saltea en silencio según el orden
      lexicográfico. `002_tenant_schema_template.sql` modela `appointments` (Scheduling,
      §16.3 no construido) en vez del dominio clínico de §10. Además
      `scripts/create_tenant_schema.sql` invoca `\i` —meta-comando de psql— dentro de un
      `EXECUTE` server-side, donde no puede funcionar. Adoptar Flyway acá (§9) y
      consolidar, no apilar una migración más encima.
- [ ] `migrations/003_demo_marker.sql` + `DemoModeGuard` — AC-01, AC-02
- [ ] CI: tracked-database check (AC-03) + single-auth-implementation check (AC-04)
- [ ] `AuditLog` entity — append-only, trigger de PostgreSQL bloqueando UPDATE/DELETE
- [ ] `AuditAspect` (AOP) — intercepta métodos `@Auditable`, persiste transaccionalmente
      con la operación principal; si la operación falla, registra `result = ERROR`
- [ ] Test: usuario de aplicación de la DB no tiene grant DELETE sobre `audit_log` — AC-10
- [ ] Test: cada lectura de PHI produce exactamente 1 fila nueva en `audit_log` — AC-07
      (test placeholder hasta que exista una entidad PHI real en Sub-fase 2)

## Sub-fase 2: Identity (gaps) + Patient + ClinicalEncounter
- [ ] `SchemaProvisioner.provisionSchema` acepta `TenantSlug`, revalida en el adapter — AC-05
- [ ] Rate limiting de login: 5 intentos → lockout 15 min + alerta
- [ ] `Patient` entity + value objects (documento, tipo sangre, alergias, afiliación EPS/
      SISBEN opcional) — FR-CLN-01
- [ ] `EncryptionService` (AES-256-GCM, IV aleatorio por operación, clave por tenant) —
      aplicado a columnas PHI de `Patient`
- [ ] Test: SELECT directo sobre columna cifrada no devuelve texto plano — AC-09
- [ ] `ClinicalEncounter` con firma (soft signature), inmutable a nivel DB tras firmar —
      FR-CLN-02
- [ ] Test: PUT sobre encounter firmado → 409 — AC-08
- [ ] Autorización cross-tenant y cross-`service_id` en todo endpoint clínico
- [ ] Test: lectura cross-tenant → 403 — AC-06; lectura cross-service dentro del mismo
      tenant → 403 — AC-06b (cobertura 100% en este path)

## Sub-fase 3: Admissions + Triage
- [ ] `Admission` entity + clasificación Triage Manchester (prioridad 1–5) — FR-CLN-03
- [ ] Vínculo `Admission` → `ClinicalEncounter` cuando se abre uno
- [ ] Tests de flujo: ingreso → triage → apertura de encounter

## Sub-fase 4: Diario de enfermería + Motor de Conocimiento
- [ ] `HealthDiaryEntry` + `VitalSigns` + `HealthIntervention` (NIC) +
      `InterventionOutcome` (NOC) + `MedicationAdministration` — FR-CLN-04, FR-CLN-05
- [ ] Índice compuesto `(diagnosis_code, nic_code, effectiveness)` — ADR-006
- [ ] Endpoint `GET /api/v1/knowledge/search` — JPQL ad-hoc, sin vista materializada
- [ ] Enforcement de k-anonimato: `COUNT(DISTINCT patient_id) >= K_ANONYMITY_THRESHOLD`
      (default 5) — FR-CLN-07
- [ ] Test: combinación casi-única de filtros → resultado suprimido, no vacío-silencioso
      — AC-14
- [ ] Verificar que la query del motor no concatena filtros de usuario sin parametrizar
      (§8.4 aplica también acá, no solo al audit log)

## Sub-fase 5: Interconsultas
- [ ] `InterconsultationRequest` + `InterconsultationResponse` — FR-CLN-08
- [ ] Validación de acceso del `SPECIALIST` **en cada request**, nunca cacheada — FR-CLN-10
- [ ] `Prescription` originada en interconsulta se vincula al `ClinicalEncounter` raíz —
      FR-CLN-09
- [ ] Test: grant → close → nuevo request del specialist → 403 — AC-13

## Sub-fase 6: Laboratorio + Farmacia
- [ ] `LabOrder` + `LabResult` con flag de valor crítico — FR-CLN-11
- [ ] `Prescription` (si no vino de Sub-fase 5) + `DispensationRecord` + índice de
      adherencia — FR-CLN-12
- [ ] Warning (no bloqueo) de conflicto alergia/misma clase de medicamento activa

## Sub-fase 7: Frontend
- [ ] React 18 + Vite + Tailwind — SPA única, vistas por rol (§4) — ADR-014
- [ ] Vistas: Admisiones, Encuentro clínico, Diario de enfermería, Motor de Conocimiento
      (con mensaje explícito cuando k<5 — FR-CLN-07 UX), Interconsultas, Labs, Farmacia
- [ ] `docker-compose.yml` completa el placeholder de Sub-fase 0 con el frontend real

## Sub-fase 8: Verificación de seguridad end-to-end
- [ ] Ampliar `.semgrep/` a concatenación JPQL, no solo `String sql = "..." + $X`
- [ ] `sqlmap --level 3` contra instancia local — cubre vector de cabeceras — AC-11
- [ ] Reporte commiteado en `docs/security/`
- [ ] CI en verde real: quitar cualquier `|| true` remanente; confirmar con un branch de
      prueba que un test roto pone CI en rojo

## Criterios de completitud del milestone
- [ ] Todos los AC de §18.1 y §18.2 del SRS v3.0 en "Pass"
- [ ] `docs/SRS.md` §5 y §16.1 actualizados con el estado real por módulo
- [ ] `tasks/lessons.md` actualizado
- [ ] Walkthrough grabado (GIF/video) para presentación de portafolio — sustituye al
      demo público que se decidió no desplegar (ADR-015)

## Revisión
[Se completa al cerrar el milestone]