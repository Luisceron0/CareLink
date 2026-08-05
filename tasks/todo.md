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
- [x] **Arreglar `migrations/` antes de agregarle nada** (descubierto en Sub-fase 0).
      Resuelto adoptando Flyway (§9): `V1__identity_baseline.sql` consolida `tenants`/
      `users`/`sessions`/`verification_tokens` en una sola forma, derivada de las
      entidades JPA. `migrations/` (raíz) y `scripts/create_tenant_schema.sql` (el `\i`
      dentro de un `EXECUTE` que no podía funcionar) se eliminaron — Flyway y
      `PostgresSchemaProvisioner` son ahora el único camino de esquema.
- [x] `V2__demo_marker.sql` + `DemoModeGuard` — AC-01, AC-02
      Evidencia: `ContainmentGuardIT`, 5 tests — boot falla sin `DEMO_MODE=true`, con
      `APP_ENV` de producción, y contra una base sin el sello (`flyway.target=1`,
      esquema válido pero sin sello — la forma peligrosa a propósito); y arranca cuando
      las tres condiciones se cumplen (contrapeso: sin este test, un guard que
      rechazara todo también pasaría los otros cuatro).
- [x] CI: tracked-database check (AC-03) + single-auth-implementation check (AC-04)
      Ambos bloqueantes (`exit 1`, sin `|| true`), en `.github/workflows/ci.yml`. El paso
      de build pasa de `mvn -DskipTests package` a `mvn verify` — ahora CI corre los
      unitarios y los `*IT` (antes ninguno de los dos). No verificado corriendo GitHub
      Actions de verdad (sin acceso a esa infraestructura desde acá) — sí verificado que
      cada comando individual (`mvn verify`, los dos greps) se comporta como se espera
      localmente.
- [x] **Dos roles de base de datos** (no estaba en el plan original, pero AC-10 es
      inalcanzable sin esto): con un solo rol conectando como superusuario —como
      arrancó el compose de Sub-fase 0— el rechazo de permisos no significa nada. Rol
      administrador (Flyway, `PostgresSchemaProvisioner`) + `carelink_app` restringido
      (JPA, tráfico normal). Provisionado por `docker/postgres-init/01-create-app-
      role.sh` al inicializar el volumen de Postgres, no por una migración de Flyway —
      si se creara ahí habría una carrera entre "el rol existe" y "el pool de conexiones
      de la app intenta su primera conexión".
- [x] `AuditLog` — append-only, por tenant (`db/tenant/tenant_template.sql`), trigger de
      PostgreSQL bloqueando UPDATE/DELETE para cualquier rol, incluido el admin.
      De paso: se sacaron `physicians` y `appointments` de esa plantilla — Scheduling
      (§16.3) y un duplicado de `User`, ninguno de los dos pertenece ahí.
- [x] `AuditAspect` (AOP) — intercepta métodos `@Auditable`, persiste vía
      `AuditEntryPort`/`JdbcAuditEntryAdapter`; si la operación falla, registra
      `result = ERROR` y re-lanza. Evidenciado con un caso de uso sintético
      (`AuditAspectIT`) — todavía no hay un caso de uso real que auditar.
      **Alcance declarado, no completo:** la garantía "persiste transaccionalmente con
      la operación principal" no está reverificada contra un `@Transactional` real
      (no existe ninguno con escritura de PHI todavía). Se reverifica en Sub-fase 2.
- [x] Test: usuario de aplicación de la DB no tiene grant DELETE sobre `audit_log` — AC-10
      `AuditLogAppendOnlyIT`: el rol de aplicación puede INSERT/SELECT, no DELETE/UPDATE
      (rechazado por permiso); el rol admin sí tiene el permiso pero lo bloquea el
      trigger (dos capas independientes, verificadas por separado).
- [ ] Test: cada lectura de PHI produce exactamente 1 fila nueva en `audit_log` — AC-07
      (test placeholder hasta que exista una entidad PHI real en Sub-fase 2)

### Descubierto durante la Sub-fase 1 (no estaba en el plan original)
- [x] **`DataSourceConfig` — tres trampas de `@ConditionalOnMissingBean` seguidas.**
      Un bean propio de tipo `DataSource` hizo que Spring Boot dejara de autoconfigurar
      el primario (JPA terminaba corriendo como administrador); la corrección con
      `@ConfigurationProperties` sobre `DataSourceBuilder` no aplicaba la URL (Hikari usa
      `setJdbcUrl`, no `setUrl`); corregido eso, el mismo patrón volvió a pasar un nivel
      arriba con `JdbcTemplate`. Ninguna capa la encontró un test hasta que se escribió
      uno a propósito (`ApplicationContextLoadsTest.primaryDataSourceConnectsAsRestrictedRoleNotAdmin`)
      y se verificó contra `docker compose up` + `pg_stat_activity` real. Detalle
      completo en `tasks/lessons.md`, 2026-08-05.
- [x] Pool del DataSource administrador dimensionado a 2 conexiones (Hikari default es
      10) — su único consumidor es un evento raro (provisión de tenant), no tráfico
      caliente. 10 conexiones administrador ociosas por instancia no daban ningún
      beneficio.

## Sub-fase 2: Identity (gaps) + Patient + ClinicalEncounter
- [x] **Contexto de tenant en el request autenticado** (no estaba en el plan, pero
      Patient/ClinicalEncounter no pueden saber en qué schema operar sin esto). El JWT
      ya llevaba `tenant_id` desde Sub-fase 1, pero `JwtAuthenticationFilter` nunca lo
      extraía — el principal era el `sub` crudo. `AuthenticatedPrincipal` (userId,
      tenantId, role) reemplaza el `String` suelto. Implementa la interfaz
      `org.springframework.security.core.AuthenticatedPrincipal` y sobreescribe
      `getName()` explícitamente — sin eso, `Authentication.getName()` habría devuelto
      la representación completa del record en vez del userId, rompiendo en silencio
      tanto `AuditAspect` (identificación de usuario en el audit log) como
      `ProtectedController`. Encontrado razonando sobre el cambio antes de correrlo, no
      después — el test existente (`$.sub` en `AuthControllerSecurityIT`) lo habría
      detectado igual, pero mejor no depender de eso.
- [x] `SchemaProvisioner.provisionSchema` acepta `TenantSlug`, revalida en el adapter — AC-05
      `TenantSlug.PATTERN` expuesto como única fuente de verdad del regex (antes había
      dos copias divergentes: la del value object y la, más estricta, de
      `JdbcAuditEntryAdapter`). `PostgresIdentifiers.quote(...)` agregado como segunda
      capa — comillar el identificador, no solo validarlo, de paso arregla el bug de
      guión-en-identificador encontrado en Sub-fase 1 (`CREATE SCHEMA tenant_alguna-clinica`
      fallaba con error de sintaxis SQL crudo). `RegisterTenantUseCase` pasa el value
      object, no `slug.value()`. Evidencia: `PostgresSchemaProvisionerIT` — provisiona
      un slug con guión con éxito, y confirma que un slug malicioso no puede llegar a
      `provisionSchema` porque el port exige `TenantSlug`, no `String`. 14 tests de
      integración en verde (antes 12).
- [x] Rate limiting de login: 5 intentos → lockout 15 min + alerta — FR-ID-03
      `LoginRateLimiter`, en memoria por instancia (no Redis — §9, sin entorno con más
      de una instancia, §1.6/ADR-015). Clave por IP vía `request.getRemoteAddr()`, no
      `X-Forwarded-For` — sin proxy real delante en este milestone, confiar en esa
      cabecera dejaría que cualquier cliente elija con qué IP se lo limita cambiando un
      header (§8.4). "Alerta" es un log estructurado en WARN, no un canal externo —
      §16.4 excluye integraciones vivas y §14 ya declara que los logs se inspeccionan a
      mano en esta etapa. De paso, corregido: login inválido devolvía 500 (ninguna
      excepción manejada llegaba a un catch), ahora 401; el bloqueo devuelve 429.
      Evidencia: `AuthControllerSecurityIT` — 5 fallos consecutivos bloquean el sexto
      intento aunque las credenciales sean correctas (verifica que el lockout se chequea
      antes de tocar el caso de uso, no que simplemente rechaza credenciales malas de
      nuevo). `LoginRateLimiter` se importa REAL en ese test, no mockeado.
      **No resuelto acá, hallazgo aparte:** no existe `GlobalExceptionHandler` en todo
      el controller — el 401 de login se corrigió puntualmente porque tocaba ese código
      de todos modos, pero otras excepciones sin manejar (`/refresh`, `/verify`) siguen
      devolviendo 500 genérico. SRS §8.1 lo declara como mitigación de la fila
      "Unhandled exception discloses internals" y no está construido.
- [x] `Patient` entity + value objects (documento, tipo sangre, alergias, afiliación EPS/
      SISBEN opcional) — FR-CLN-01
      Primer corte: fullName, DocumentId (tipo+número, con validación básica cédula vs.
      pasaporte), fecha de nacimiento, sexo, tipo de sangre, alergias. Contacto, contacto
      de emergencia, medicación activa y afiliación EPS/SISBEN quedan diferidos a
      propósito — extender es agregar campos, no rediseñar (ver el javadoc de Patient).
      `JdbcPatientRepository`: JDBC directo con schema comillado, no JPA — mismo patrón
      que `JdbcAuditEntryAdapter` (Patient vive en schema dinámico por tenant, JPA no
      resuelve eso sin adoptar multi-tenencia completa de Hibernate). Cifra por campo al
      guardar, descifra al leer — el dominio nunca ve un valor cifrado.
      `POST/GET /api/v1/patients`, verificado por HTTP real contra `docker compose up`:
      201 al crear, 200 al leer con datos correctos, fila cruda en la base cifrada,
      `audit_log` con `PATIENT_CREATE` y `PATIENT_READ`, y un segundo tenant leyendo el
      paciente del primero → 403 (AC-06, ver más abajo).
      **Hallazgo real durante esta tarea:** mover `EncryptionService` al paquete
      `com.carelink.clinical` (para alinear con la estructura ya documentada en
      copilot-instructions.md) rompió el component-scan de Spring — `clinical` es
      HERMANO de `identity`, no hijo, y `@SpringBootApplication` solo escanea hacia
      abajo desde su propio paquete. Ningún bean de `clinical` se registraba, en NINGÚN
      entorno, incluida la app real. Corregido con `scanBasePackages` explícito en
      `Application.java`. Encontrado por el primer test de `PatientLifecycleIT`, no
      por inspección — el mismo patrón de "un cambio que parece prolijo tiene una
      consecuencia de runtime no obvia" que ya pasó dos veces con `DataSourceConfig`
      en Sub-fase 1 (ver `tasks/lessons.md`).
      **`RegisterPatientUseCase`/`GetPatientUseCase` son `@Component`, no instanciados a
      mano como el resto de los casos de uso del repo** — necesario para que
      `@Auditable` los intercepte (Spring solo aplica proxies AOP a sus propios beans).
      Documentado en el javadoc de ambas clases como excepción deliberada al patrón
      existente, no una inconsistencia accidental.
- [x] `EncryptionService` (AES-256-GCM, IV aleatorio por operación, clave por tenant) —
      aplicado a columnas PHI de `Patient`
      `AesGcmEncryptionService`: clave maestra (`CLINIC_ENCRYPTION_KEY`, 32 bytes) +
      clave por tenant DERIVADA vía HMAC-SHA256 sobre el slug — decisión de diseño, no
      cerrada del todo por el SRS (§8.3 pide "clave por tenant" sin especificar
      almacenamiento; no existe todavía un Vault por tenant). Documentado en el javadoc
      de la clase como punto a revisar si un milestone futuro agrega gestión de
      secretos por tenant. Falla al construirse sin la clave — arrancar con cifrado
      efectivamente desactivado no es una opción.
- [x] Test: SELECT directo sobre columna cifrada no devuelve texto plano — AC-09
      `PhiColumnCannotBeReadAsPlaintextIT`: INSERT vía rol de aplicación de un valor ya
      cifrado en `tenant_ac09tenant.patients.full_name`, SELECT directo confirma que no
      es el texto plano ni lo contiene, y `decrypt(...)` confirma que sigue siendo el
      dato correcto. `AesGcmEncryptionServiceTest` (7 tests) cubre el algoritmo en
      aislamiento: round-trip, IV distinto en cada cifrado del mismo valor, clave
      distinta por tenant (un ciphertext de un tenant no descifra bajo el slug de otro),
      y que un valor alterado falla la verificación GCM en vez de decodificar a basura
      silenciosamente. De paso: `tenant_template.sql` no otorgaba ningún grant sobre
      `patients` — solo `audit_log` lo tenía — agregado SELECT/INSERT para `{{app_role}}`.
- [ ] `ClinicalEncounter` con firma (soft signature), inmutable a nivel DB tras firmar —
      FR-CLN-02
- [ ] Test: PUT sobre encounter firmado → 409 — AC-08
- [x] Test: lectura cross-tenant → 403 — AC-06 (para `/api/v1/patients`, el único
      endpoint clínico que existe hasta ahora)
      No es una comparación en runtime "¿el tenant pedido es el mío?" — no existe un
      tenant pedido que comparar: `PatientController` resuelve el tenant siempre desde
      `AuthenticatedPrincipal.tenantId()` (el JWT), nunca de un parámetro que el cliente
      controle. Un intento cross-tenant y un id inexistente devuelven la misma respuesta
      (403) — indistinguibles a propósito, para que un 403 nunca confirme que el recurso
      existe en el tenant de otro. Verificado en `PatientLifecycleIT` y por HTTP real
      contra `docker compose up` (dos tenants, un JWT cada uno, el segundo intenta leer
      el paciente del primero → 403).
- [ ] Autorización cross-tenant y cross-`service_id` en todo endpoint clínico
      Sigue abierto: el patrón de AC-06 (tenant derivado del JWT, nunca de un parámetro)
      queda establecido con Patient, pero "en TODO endpoint clínico" recién se puede
      cerrar cuando haya más de un endpoint para reverificar contra él.
- [ ] Test: lectura cross-service dentro del mismo tenant → 403 — AC-06b (cobertura 100%
      en este path)
      Requiere agregar `service_id` a `User` (no existe todavía — FR-ID-02 lo pide,
      nunca se construyó) + un migration V4 + el flujo de invitación de usuarios
      asignando `service_id`. Más grande que un ítem de checklist; es su propia tarea.

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