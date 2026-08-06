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
- [x] `ClinicalEncounter` con firma (soft signature), inmutable a nivel DB tras firmar —
      FR-CLN-02
      Dos capas independientes, mismo patrón que `audit_log` (AC-10): trigger de
      Postgres que rechaza cualquier UPDATE sobre una fila con `signed_at` no nulo para
      CUALQUIER rol (SQLSTATE custom `P0409`, no una validación de aplicación
      salteable con acceso directo a la base), y `sign()` usa
      `WHERE signed_at IS NULL` para que re-firmar afecte 0 filas sin necesitar que el
      trigger intervenga en ese camino. `diagnosis_cie10` se guarda en texto plano a
      propósito (código categórico, lo necesita el futuro Motor de Conocimiento sin
      descifrar); el resto de los campos clínicos se cifra con `AesGcmEncryptionService`
      (AC-09). Mismo patrón de aislamiento por tenant y de "el actor sale del JWT, nunca
      del body" que `PatientController`.
      **Fix de compilación encontrado:** `org.postgresql:postgresql` tiene scope
      `runtime` en el pom — `org.postgresql.util.PSQLException` no está disponible en
      compilación en ningún otro punto del código. Se usa
      `java.sql.SQLException.getSQLState()` (API JDBC estándar) en vez de la clase del
      driver. Detalle en `tasks/lessons.md`, 2026-08-05.
- [x] Test: PUT sobre encounter firmado → 409 — AC-08
      `ClinicalEncounterLifecycleIT`: contrapeso explícito antes de firmar (un encounter
      SIN firmar se edita sin problema, para que el test de "firmado rechaza" pruebe
      algo específico sobre el estado firmado, no que las ediciones siempre fallen);
      edición tras firma → `EncounterAlreadySignedException` → 409 con body claro;
      re-firmar → misma excepción; contenido no mutado tras el intento rechazado.
      Verificado además por HTTP real contra `docker compose up`: POST → PUT → POST
      sign → 200, PUT tras firma → 409, fila cruda con `signed_at` seteado y notas como
      ciphertext, `audit_log` con el intento rechazado (`result = ERROR`). `mvn verify`
      completo (unit + integration vía Failsafe) en verde.
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
- [x] Autorización cross-tenant en todo endpoint clínico existente
      El patrón de AC-06 (tenant derivado de `AuthenticatedPrincipal.tenantId()` vía
      `TenantRepository.findById`, nunca de un parámetro) ahora se reverificó en dos
      endpoints independientes — `PatientController` y `ClinicalEncounterController` —
      con la misma implementación (`resolveTenantSlug`). La parte de
      cross-`service_id` queda explícitamente fuera de este ítem — ver AC-06b abajo.
- [x] Test: lectura cross-service dentro del mismo tenant → 403 — AC-06b (cobertura 100%
      en este path)
      `service_id` viaja en el JWT (claim `service_id`, seteado en el login desde
      `users.service_id`) y los tres recursos clínicos (Patient, ClinicalEncounter,
      Admission) lo llevan estampado. El filtro va en el `WHERE` del SQL, no sobre la
      fila ya traída — una fila de otro servicio nunca sale de la base. En la única
      mutación que no viene precedida de una lectura (`linkClinicalEncounter`) el
      filtro está en el `UPDATE ... WHERE`, así que no hay ventana entre "verifiqué
      que es mío" y "lo modifico".
      **Decisión de diseño:** el alcance se pasa como value object `ServiceScope`, no
      como `String` nullable. Con un `String`, `null` solo puede significar "sin
      filtro", así que cualquier camino que se olvide de setearlo FALLA ABIERTO
      (devuelve todo el tenant en vez de nada). Con el tipo, "sin filtro" hay que
      pedirlo por nombre (`ServiceScope.allServices()`) y olvidarse es un error de
      compilación. Mismo razonamiento que `TenantSlug` en AC-05. Un rol no exento sin
      `service_id` resuelve a SIN ACCESO, no a acceso irrestricto.
      Evidencia: `ServiceScopeIsolationIT` (los tres recursos, cada uno con su
      contrapeso: el mismo recurso leído con el servicio correcto SÍ vuelve) + en vivo
      contra `docker compose`: dos PHYSICIAN en Urgencias y Consulta Externa del MISMO
      tenant, ambos creados por el flujo real de invitación → leer el paciente del otro
      servicio = 403, el propio = 200, TENANT_ADMIN = 200; idem encounter, incluido
      `POST /sign` (mutación) = 403 cross-service, 200 dentro del servicio.

### Descubierto durante la Sub-fase 2 (no estaba en el plan original)
- [x] **FR-ID-02 no existía: no había ningún flujo para crear un usuario con rol
      distinto de `TENANT_ADMIN`.** Encontrado al intentar obtener un JWT con rol
      `PHYSICIAN` para verificar `ClinicalEncounterController` contra
      `docker compose up` — el registro de tenant solo creaba un `TENANT_ADMIN`, no
      había invitación ni asignación de rol.
      **Por qué importaba más allá de Sub-fase 2:** las Sub-fases 3–6 tienen endpoints
      gateados por rol (`NURSE`, `SPECIALIST`, `LAB_TECH`, `PHARMACIST`, `ADMISSIONS`).
      Sin un flujo real de asignación de rol, esos endpoints no se podían
      live-verificar contra `docker compose up` con el mismo rigor que Patient y
      ClinicalEncounter.
      **Resuelto, con confirmación explícita del usuario antes de construirlo**
      (no se amplió el alcance en silencio): `POST /api/v1/users/invite`
      (`TENANT_ADMIN` únicamente, tenant resuelto del JWT) crea el usuario invitado
      con rol + `service_id` y una contraseña aleatoria inutilizable; el invitado la
      reemplaza vía `POST /api/v1/auth/accept-invite` con el token de un solo uso que
      recibe por email (mismo mecanismo hasheado que `VerifyEmailUseCase`). Migration
      `V4__user_invitation.sql` agrega `service_id`/`active` a `users`.
      `POST /api/v1/users/{id}/deactivate` desactiva sin borrar (`ON DELETE RESTRICT`
      desde V1, retiene el historial de auditoría). `InviteUserUseCase`/
      `DeactivateUserUseCase` son `@Component`/`@Auditable` (acciones `USER_INVITE`,
      `USER_DEACTIVATE`).
      **Hallazgo real encontrado construyendo esto, no una decisión de diseño
      nueva:** el registro de tenant (FR-ID-01) estaba roto contra un
      `docker compose up` fresco — `SmtpEmailNotifier` intentaba conectar a SMTP de
      verdad y no existía ningún catcher de correo en el compose, así que CADA
      registro fallaba con `MailSendException: Connection refused`. `.env.example`
      ya documentaba la intención ("MailHog/Mailpit") pero el contenedor nunca se
      había agregado. Arreglado agregando `axllent/mailpit` a `docker-compose.yml` y
      wireando `SMTP_HOST=mailpit` — nada sale de la red local de compose (§16.4).
      **Verificado en vivo, de punta a punta, sin ningún atajo de SQL:** registro de
      tenant → login `TENANT_ADMIN` → `POST /api/v1/users/invite` (PHYSICIAN,
      service_id="Urgencias") → 201 → email real capturado en Mailpit con el token →
      `POST /api/v1/auth/accept-invite` → 200 → login como PHYSICIAN con la
      contraseña recién fijada → 200 → esa misma sesión usada para
      `POST /api/v1/encounters` real (201) → `POST /api/v1/users/{id}/deactivate` →
      login posterior del mismo usuario → 401 con el mismo mensaje genérico que
      credenciales inválidas (no revela el estado de la cuenta). Fila cruda de
      `users` con `active = false`, no borrada. `audit_log` con `USER_INVITE` y
      `USER_DEACTIVATE`, ambos `result = SUCCESS`. `mvn verify` completo (unit +
      integration vía Failsafe, incluyendo `UserManagementLifecycleIT`) en verde.
      **Known gaps, no resueltos acá:** autodesactivación no está protegida (un
      único `TENANT_ADMIN` desactivándose a sí mismo deja el tenant sin forma de
      invitar a nadie más — no estaba en el texto de FR-ID-02, no se agregó sin
      pedirlo). Registro de tenant e invitación de usuario no son transaccionales
      entre sus pasos (una falla de correo después del `INSERT` deja una fila
      utilizable) — preexistente en `RegisterTenantUseCase`, heredado por
      `InviteUserUseCase` al tener la misma forma, no introducido ni arreglado acá.
      Una excepción no capturada explícitamente en el controller (p. ej.
      `TenantAlreadyExistsException`) devuelve 403 en vez del status semánticamente
      correcto — falta de un `@ExceptionHandler` global preexistente, no introducida
      acá; se evitó localmente capturando cada excepción nueva en
      `UserManagementController`/`AuthController`, no arreglada de raíz.

## Sub-fase 3: Admissions + Triage — cerrada 2026-08-05
- [x] `Admission` entity + clasificación Triage Manchester (prioridad 1–5) — FR-CLN-03
      `TriagePriority` (value object, valida 1-5), `AdmissionType` (URGENCIAS |
      CONSULTA_EXTERNA). Prioridad de triage OBLIGATORIA para URGENCIAS y RECHAZADA
      para CONSULTA_EXTERNA — Triage Manchester es específicamente una herramienta de
      urgencias, esa lectura del texto del SRS (que no lo desambigua del todo) quedó
      documentada en `docs/SRS.md` §5.4, no dejada implícita en el código nomás.
      `POST /api/v1/admissions` gateado a rol `ADMISSIONS` (§4). Sin cifrado en
      `admissions` — a diferencia de `patients`/`clinical_encounters`, no hay ningún
      campo de texto libre con PHI, todo es categórico.
- [x] Vínculo `Admission` → `ClinicalEncounter` cuando se abre uno
      `POST /api/v1/admissions/{id}/link-encounter`, gateado a `PHYSICIAN` — quien abre
      el encounter es quien sabe a qué admisión corresponde, no quien hizo el ingreso.
      `LinkEncounterToAdmissionUseCase` es `@Component`/`@Auditable`
      (`ADMISSION_LINK_ENCOUNTER`).
- [x] Tests de flujo: ingreso → triage → apertura de encounter
      `AdmissionLifecycleIT`: ingreso URGENCIAS con triage 2 → apertura de
      ClinicalEncounter → vínculo → re-lectura confirma `clinicalEncounterId`
      poblado; URGENCIAS sin prioridad y CONSULTA_EXTERNA con prioridad ambos
      rechazados (con el contrapeso de que CONSULTA_EXTERNA sin prioridad sí es
      válida); AC-06 aplicado a admisiones (lectura cross-tenant → vacío).
      Verificado en vivo contra `docker compose up`, de punta a punta, con usuarios
      reales invitados vía FR-ID-02 (sin atajo de SQL): registro de tenant → invitar
      ADMISSIONS y PHYSICIAN → aceptar ambas invitaciones → login real de cada uno →
      `POST /api/v1/admissions` como ADMISSIONS (201) → el mismo intento como
      PHYSICIAN (403, confirma el gate de rol) → URGENCIAS sin prioridad (400, y esa
      fila de rechazo quedó en `audit_log` con `result = ERROR`) → PHYSICIAN abre
      encounter → `link-encounter` (200) → re-lectura muestra el vínculo. `mvn verify`
      completo en verde.

## Sub-fase 4: Diario de enfermería + Motor de Conocimiento — cerrada 2026-08-05
- [x] `HealthDiaryEntry` + `VitalSigns` + `HealthIntervention` (NIC) +
      `InterventionOutcome` (NOC) — FR-CLN-04, FR-CLN-05
      La entrada se asocia a Patient + fecha/turno, NO a un encounter abierto (§10).
      Texto libre cifrado; códigos NIC/NOC/NANDA/CIE-10 y mediciones de vitales en
      claro (una presión de 120/80 no identifica a nadie — mismo criterio que
      `blood_type`). Registrar el outcome es de una sola dirección
      (`UPDATE ... WHERE effectiveness IS NULL`): un segundo POST no sobreescribe, esa
      evaluación ya alimentó agregados que se leen como evidencia clínica.
      **`MedicationAdministration` NO se construyó** — estaba en este ítem del plan
      pero no en FR-CLN-04 como entidad propia con endpoint; su lugar natural es
      Sub-fase 6 junto a `DispensationRecord` (farmacia), donde el circuito de
      medicación existe completo. Anotado como diferido, no como hecho.
- [x] Índice compuesto `(diagnosis_code, nic_code, effectiveness)` — ADR-006
      Existe como UN índice porque el outcome se almacena en la misma tabla que la
      intervención. §10 modela `InterventionOutcome` como entidad aparte y el dominio
      Java lo respeta, pero en dos tablas ese índice no puede existir como uno solo, y
      partirlo cambiaría el plan que ADR-006 dimensionó para <2s con 50k entradas. La
      fusión es de almacenamiento, no de modelo — documentado en el javadoc y en el SQL.
- [x] Endpoint `GET /api/v1/knowledge/search` — SQL ad-hoc, sin vista materializada
- [x] Enforcement de k-anonimato: `COUNT(DISTINCT patient_id) >= K_ANONYMITY_THRESHOLD`
      (default 5) — FR-CLN-07
      El umbral se aplica en un `HAVING` DENTRO de la consulta, no descartando filas en
      Java: si viviera en memoria, las filas por debajo del umbral igual habrían salido
      de la base. Cuenta pacientes DISTINTOS, no intervenciones. `SearchKnowledgeUseCase`
      se niega a arrancar con un umbral < 2 (una env var mal puesta no debe poder
      desactivar en silencio algo que ADR-007 declara no negociable).
- [x] Test: combinación casi-única de filtros → resultado suprimido, no vacío-silencioso
      — AC-14
      `HealthDiaryAndKnowledgeEngineIT`: 4 pacientes → suprimido, el quinto → aparece
      (el contrapeso importa el doble acá: un motor que suprimiera TODO pasaría un test
      de supresión trivialmente); 10 intervenciones sobre UN paciente → sigue suprimido;
      "suprimido" y "no hay casos" son respuestas distinguibles. Verificado en vivo por
      HTTP con las tres respuestas.
- [x] Verificar que la query del motor no concatena filtros de usuario sin parametrizar
      (§8.4 aplica también acá, no solo al audit log)
      Lo dinámico es solo la presencia/ausencia de fragmentos `AND columna = ?` fijos
      escritos en el archivo; todo valor viaja como parámetro posicional. Lo único
      interpolado es el nombre del schema, que viene de configuración de despliegue y
      aun así pasa por revalidación + comillado (AC-05).
- [ ] **Diferido a Sub-fase 6:** `MedicationAdministration` (ver arriba).
- [ ] **Gap conocido, falla ruidosamente:** el filtro por edad de FR-CLN-06 devuelve 501.
      `date_of_birth` está cifrada (AC-09) y un ciphertext no admite comparación por
      rango en SQL. Resolverlo requiere una columna derivada de banda etaria (PHI
      debilitada, necesita su propio análisis), cifrado que preserve el orden (rompe
      AC-09), o descifrar y filtrar en memoria (saca de la base justo las filas que
      k-anonimato protege). Lanza en vez de ignorar el filtro: ignorarlo devolvería un
      conjunto MÁS AMPLIO que el pedido presentándolo como el pedido.

## Sub-fase 5: Interconsultas — cerrada 2026-08-05
- [x] `InterconsultationRequest` + `InterconsultationResponse` — FR-CLN-08
      Responder solo es posible sobre una interconsulta ABIERTA y dirigida a ese
      especialista: responder una cerrada sería escribir en una historia clínica a la
      que ya no se tiene acceso.
- [x] Validación de acceso del `SPECIALIST` **en cada request**, nunca cacheada — FR-CLN-10
      No hay tabla de permisos ni fila de "concedido": cada request reejecuta una
      consulta (`¿existe interconsulta OPEN para este especialista y este paciente?`).
      Cerrar es un `UPDATE status` y con eso la siguiente evaluación devuelve false —
      no hay un segundo paso de revocación que alguien pueda olvidar, porque lo que
      habría que revocar nunca se persistió. El acceso es POR PACIENTE, no un permiso
      general del especialista.
      **Interacción deliberada con AC-06b:** el especialista suele estar en OTRO
      servicio que el médico solicitante (es el punto de una interconsulta), así que
      filtrar por servicio le negaría justo el acceso que la interconsulta concede.
      Para `SPECIALIST`, la comprobación de interconsulta abierta REEMPLAZA al filtro
      de servicio — es un permiso más ESTRECHO, no más amplio: por paciente y con
      vencimiento al cerrar, contra uno permanente y de todo el departamento.
- [x] `Prescription` originada en interconsulta se vincula al `ClinicalEncounter` raíz —
      FR-CLN-09
      El `clinical_encounter_id` sale de la interconsulta, NUNCA del body, así que el
      cliente no puede colgar la prescripción de otro encounter. `NOT NULL`: una
      prescripción sin encounter de origen no es trazable, y la trazabilidad es el
      requisito entero.
- [x] Test: grant → close → nuevo request del specialist → 403 — AC-13
      `InterconsultationLifecycleIT` (5 tests) con contrapeso en ambos extremos: antes
      de que exista la interconsulta NO hay acceso (sin eso, un método que devolviera
      siempre false pasaría la mitad de "revocado" sin probar nada), con ella abierta
      sí, y tras cerrar la misma llamada devuelve false. Verificado en vivo por HTTP:
      SPECIALIST de Cardiologia (otro servicio que el PHYSICIAN de Urgencias) lee 200,
      responde 200, prescribe 201 sobre el encounter raíz; el médico cierra; el MISMO
      JWT del especialista, sin re-login, obtiene 403 en leer, responder y prescribir.

## Sub-fase 6: Laboratorio + Farmacia — cerrada 2026-08-06
- [x] `LabOrder` + `LabResult` con flag de valor crítico — FR-CLN-11
      `critical_value` es un flag EXPLÍCITO que carga el laboratorio, no algo derivado
      de comparar contra un rango: los rangos dependen del método, el equipo y la
      población, y derivarlo acá sería inventar un criterio clínico.
      "Notificar al médico solicitante" es una FILA que el médico consulta, no un side
      effect que se pierde si nadie miraba (§16.4 deja fuera email/SMS). Resultado y
      notificación se escriben en UNA transacción: un valor crítico guardado sin su
      notificación es justo el fallo que el requisito previene. Solo el destinatario
      puede acusar recibo. Cargar el resultado es write-once — sobreescribir un
      resultado emitido es corregir una historia clínica, no un UPDATE silencioso.
- [x] `Prescription` (vino de Sub-fase 5) + `DispensationRecord` + índice de
      adherencia — FR-CLN-12
      El ratio NO se recorta en 1.0: dispensar más de lo prescrito es una señal clínica
      real (error de dispensación, prescripción cambiada fuera del sistema) y aplanarlo
      escondería el caso que vale la pena mirar. Sin total de dosis registrado, la
      adherencia es NO CALCULABLE, no 0% — "no se registró el total" y "el paciente no
      tomó nada" son afirmaciones clínicas distintas.
- [x] Warning (no bloqueo) de conflicto alergia/misma clase de medicamento activa
      Devuelve 200 con la lista, nunca 409: un status de bloqueo sería exactamente el
      bloqueo que FR-CLN-12 prohíbe. Los dos tipos se detectan distinto por una razón
      concreta: las alergias están CIFRADAS (AC-09) y no se comparan en SQL — se
      descifra la fila de ESE paciente y se compara en memoria (una fila, no un
      barrido); `medication_class` está en claro justamente para que el chequeo de
      misma clase sí pueda ser una consulta. La coincidencia de alergia es
      deliberadamente amplia: en una advertencia que no bloquea, un falso positivo
      cuesta una lectura y un falso negativo cuesta una reacción alérgica. NO es un
      motor de interacciones farmacológicas — no hay catálogo de principios activos y
      no se inventa uno.
- [x] `MedicationAdministration` (diferido desde Sub-fase 4)
      Cubierto funcionalmente por `DispensationRecord`: es el registro de qué se
      entregó, cuánto y quién, que es lo que el índice de adherencia necesita. Una
      entidad separada de "administración" (enfermería administrando la dosis, distinto
      de farmacia dispensándola) NO se construyó — FR-CLN-12 define la adherencia sobre
      dosis dispensadas y no distingue los dos actos. Anotado como decisión, no como
      olvido.

## Sub-fase 7: Frontend — cerrada 2026-08-06
- [x] React 18 + Vite + Tailwind — SPA única, vistas por rol (§4) — ADR-014
      **El access token vive SOLO en memoria** (variable de módulo), nunca en
      localStorage/sessionStorage: un XSS puede leer el storage, no una closure. El
      refresh token queda en la cookie HttpOnly que setea el backend, que este código
      no puede tocar ni por accidente. El costo es que un F5 pierde la sesión hasta que
      /refresh la reconstruye — es el trade que se quiere.
      **La navegación filtrada por rol es UX, no seguridad:** ocultar un link no impide
      llamar al endpoint, y cada endpoint valida rol, tenant y servicio por su cuenta.
      Quien fuerce una URL que no le toca ve la vista vacía con el 403 del backend, no
      datos. El frontend nunca es la capa que decide un permiso.
- [x] Vistas: Admisiones, Encuentro clínico, Diario de enfermería, Motor de Conocimiento
      (con mensaje explícito cuando k<5 — FR-CLN-07 UX), Interconsultas, Labs, Farmacia
      Nueve vistas en total (además Pacientes, Login y Gestión de usuarios/FR-ID-02).
      La vista del Motor de Conocimiento tiene TRES estados de resultado, no dos:
      "Datos insuficientes" (supresión por k-anonimato) es visualmente distinto de "Sin
      casos previos", que es exactamente lo que FR-CLN-07 exige — confundirlos lleva a
      creer que una intervención nunca se usó cuando sí, solo que sobre pocos pacientes.
- [x] `docker-compose.yml` completa el placeholder de Sub-fase 0 con el frontend real
      Dockerfile multi-stage: la imagen final tiene archivos estáticos y nginx, sin
      Node ni node_modules ni el código fuente. nginx sirve la SPA (con fallback a
      index.html para el routing del cliente) y hace de proxy de /api dentro de la red
      de compose — el bundle solo usa rutas relativas, no hay URL de backend embebida.
      NO reenvía X-Forwarded-For a propósito: el backend limita por getRemoteAddr()
      (§8.4) y reenviarlo reintroduciría el vector que esa decisión evita.
      **Verificado en un navegador real** (agent-browser + Chrome headless, instalado
      para esto): login por UI como NURSE, navegación mostrando solo sus cuatro
      secciones (sin Admisiones, Encuentros ni Usuarios), búsqueda en el Motor de
      Conocimiento devolviendo "Datos insuficientes" con 1 paciente sembrado y "Sin
      casos previos" con un diagnóstico inexistente — los dos estados distinguibles en
      pantalla, no solo en el JSON.

## Sub-fase 8: Verificación de seguridad end-to-end — cerrada 2026-08-06
Reporte completo: `docs/security/AUDIT-2026-08-06.md`.
- [x] Ampliar `.semgrep/` a concatenación JPQL, no solo `String sql = "..." + $X`
      La regla original cubría una forma que ESTE codebase no usa (los repositorios
      arman el SQL con StringBuilder o lo pasan directo a jdbcTemplate), así que llevaba
      varias sub-fases en verde sobre código que nunca había mirado. Tres reglas ahora:
      la original, concatenación dentro de llamadas JDBC, y JPQL/nativas.
      **Detalle que costó descubrir:** los patrones usan `$A + $B` y no `"..." + $X`
      porque en Java `"x" + v + "y"` se parsea como `(("x" + v) + "y")` — un patrón
      anclado al literal izquierdo NO matchea la inyección clásica. Se descubrió
      escribiendo un archivo deliberadamente vulnerable y viendo que la primera versión
      no lo detectaba. Verificación final: 3 hallazgos sobre el vulnerable, 0 sobre el
      código real.
- [x] `sqlmap --level 3` contra instancia local — cubre vector de cabeceras — AC-11
      No inyectable en `/patients/{id}` ni en `/knowledge/search` (el de mayor
      superficie: 3 query params con SQL dinámico), incluidos User-Agent y Referer.
      `/auth/login` quedó NO CONCLUYENTE: el rate limiter de FR-ID-03 frenó el escaneo
      ("target appears to be rate-limiting requests"). Se documenta como tal, con la
      evidencia estática que sí hay (query derivada de Spring Data, cero `@Query`
      manuales en todo el proyecto).
- [x] Reporte commiteado en `docs/security/`
- [x] CI: gate nuevo contra secretos con default permisivo
      **Hallazgo crítico:** `TokenHasher` caía a `getOrDefault(..., "dev-refresh-secret")`
      — hasheaba TODOS los refresh tokens con un secreto escrito en el repositorio si
      faltaba la env var. El gate de AC-04 NO lo detectó porque busca el literal
      `dev-secret`, que no matchea `dev-refresh-secret`. Corregido: la app no arranca
      sin el secreto (+ `RefreshSecretGuard` para que falle en el arranque y no en el
      primer login). Gate nuevo que busca el PATRÓN estructural, no un literal, y
      `SecretConfigurationGuardTest` que lo cubre por comportamiento. Los tres,
      verificados rompiéndolos a propósito.
- [x] Healthcheck del frontend siempre en rojo (IPv6/`localhost` vs nginx IPv4)
      Un healthcheck permanentemente en rojo deja de ser señal, igual que el `|| true`.

## Criterios de completitud del milestone
- [x] Todos los AC de §18.1 y §18.2 del SRS v3.0 en "Pass"
      Los 15 (AC-01 a AC-14, AC-06b incluido). AC-11 con la salvedad documentada de
      `/auth/login` (H-05 del reporte).
- [x] `docs/SRS.md` §5 y §16.1 actualizados con el estado real por módulo
- [x] `tasks/lessons.md` actualizado
- [ ] Walkthrough grabado (GIF/video) para presentación de portafolio — sustituye al
      demo público que se decidió no desplegar (ADR-015)
      **Único ítem abierto del milestone.** Requiere grabar pantalla, que no se puede
      hacer desde acá — queda para el autor. El stack levanta con `docker compose up` y
      el frontend en :5173 ya cubre las Sub-fases 1 a 6.
- [ ] `git filter-repo` para purgar `test_identity.db` del historial (Sub-fase 0, T3)
      Sigue reservado a decisión explícita del autor: reescribe historia y obliga a
      force-push sobre un PR ya mergeado.

## Criterios de completitud del milestone
- [ ] Todos los AC de §18.1 y §18.2 del SRS v3.0 en "Pass"
- [ ] `docs/SRS.md` §5 y §16.1 actualizados con el estado real por módulo
- [ ] `tasks/lessons.md` actualizado
- [ ] Walkthrough grabado (GIF/video) para presentación de portafolio — sustituye al
      demo público que se decidió no desplegar (ADR-015)

## Revisión
[Se completa al cerrar el milestone]