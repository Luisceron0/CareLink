# Lecciones aprendidas

## [2026-08-03] — Dos implementaciones del mismo bounded context divergen en silencio
**Contexto:** revisión de arquitectura previa a retomar el proyecto. `identity-service`
(Java, RS256, Argon2id, tenant-aware) y `api-gateway-identity` (Python, HS256 con fallback
`dev-secret`, SQLite, sin tenant) exponían ambos `POST /api/v1/auth/login`.
**Error cometido:** se construyó un segundo path de auth "más simple para probar algo
rápido" sin eliminar el primero ni documentarlo en el SRS.
**Consecuencia:** cualquier revisor que abriera el repo podía encontrar la implementación
débil primero. El fallback `dev-secret` era un token forjable si alguien desplegaba esa
rama por error.
**Corrección:** `api-gateway-identity` eliminado del árbol (ADR-010). Gate de CI que
falla si aparecen los strings `HS256` o `dev-secret` bajo `services/`.
**Regla para el futuro:** un bounded context tiene una sola implementación. Si aparece la
tentación de crear una versión paralela "para probar rápido", esa tentación se resuelve
extendiendo tests sobre la implementación existente, no bifurcando.
**Tags:** #arquitectura #seguridad

## [2026-08-03] — Una invariante validada solo en el caller no es una invariante
**Contexto:** `PostgresSchemaProvisioner.provisionSchema(String tenantSlug)` concatena el
slug en `CREATE SCHEMA IF NOT EXISTS tenant_" + slug`. Hoy el único caller (Java) valida con
el value object `TenantSlug` antes de llamarlo — así que no es explotable *ahora*.
**Error cometido:** la firma del port acepta `String`, no `TenantSlug`. La protección vive
en la disciplina del caller, no en el tipo.
**Consecuencia:** el gateway Python armaba slugs con `legal_name.lower().replace(" ","-")`
sin ninguna validación — un segundo caller ya había roto la invariante antes de que se
detectara.
**Corrección:** el port pasa a aceptar `TenantSlug`, con revalidación `^[a-z0-9-]{3,64}$`
dentro del adapter (defensa en profundidad: el tipo no evita que alguien construya un
`TenantSlug` con un regex distinto en otro punto del código).
**Regla para el futuro:** si un valor tiene una invariante de seguridad, esa invariante se
codifica en el tipo que cruza el port, no en la disciplina de quien lo llama. "El caller ya
valida" es una promesa que un refactor rompe sin que nadie lo note.
**Tags:** #seguridad #arquitectura

## [2026-08-03] — Un archivo `.db` versionado no lo detecta un scanner de secrets
**Contexto:** `test_identity.db` (SQLite, con hashes Argon2id de usuarios de prueba)
estaba trackeado en git. Gitleaks no lo marcó — no matchea el patrón de un secret,
matchea patrones de texto.
**Error cometido:** confiar en que "pasamos gitleaks" cerraba el tema de datos sensibles
en el repo.
**Consecuencia:** falso sentido de seguridad. Un archivo binario con contenido sensible
puede vivir en el repo indefinidamente sin que ningún gate lo detecte.
**Corrección:** gate explícito de CI que falla si `git ls-files '*.db'` no está vacío.
Gitleaks cubre secrets en texto; este gate cubre datos estructurados versionados por error.
**Regla para el futuro:** "el scanner de secrets no lo marcó" no es lo mismo que "no hay
datos sensibles en el repo". Cada clase de dato sensible (secrets, PHI sintética, DBs
completas) necesita su propio chequeo, no un scanner genérico asumido como cobertura total.
**Tags:** #seguridad #testing

## [2026-08-03] — Un gate de CI con `|| true` no es un gate, es una nota
**Contexto:** `.github/workflows/ci.yml` tenía `mvn test || true`, `pytest -q || true`,
`dependency-check ... || true`, `pip-audit ... || true`. El SRS v1.0 declaraba 12 gates
bloqueantes.
**Error cometido:** escribir el paso de CI como si bloqueara, sin verificar que
efectivamente detiene el merge cuando falla.
**Consecuencia:** el pipeline estaba siempre en verde independientemente del resultado real
de los tests o del scan de dependencias — cero señal, apariencia de cobertura completa.
**Corrección:** `|| true` eliminado de todo gate que se declara bloqueante en el SRS
(§13.3 v2.0). Lo que no se puede hacer bloqueante todavía se marca `[PENDIENTE]`
explícitamente, no se simula con un paso verde falso.
**Regla para el futuro:** un gate de CI se verifica rompiéndolo a propósito una vez, antes
de confiar en él. Si el pipeline sigue en verde con un test fallando adentro, el gate no
existe, existe la ilusión de él.
**Tags:** #testing #deuda-técnica

## [2026-08-03] — Fusionar dos SRS no es fusionar dos arquitecturas
**Contexto:** decisión de combinar CareLink (SaaS multi-tenant) y ClinicTrack ESE
(sistema institucional single-org) en un solo sistema, a pedido explícito del autor
contra la recomendación de Arch-Sentinel.
**Riesgo identificado:** el conflicto real entre los dos proyectos no era técnico
(schema-per-tenant se resuelve modelando el ESE como un tenant más) — era de
posicionamiento de producto (SaaS comercial autoregistrable vs. sistema institucional
provisionado por TI) y de scope (11 módulos de ClinicTrack vs. el recorte a "mínimo" que
ya se había decidido en ADR-009).
**Resolución:** se adoptó la arquitectura de CareLink como sustrato y el dominio de
ClinicTrack como implementación real del módulo Clinical Records — no una fusión literal
de los dos documentos, sino una integración con roles claros para cada fuente. El scope
ampliado se contuvo dividiendo el milestone en 9 sub-fases verticales demostrables por
separado, para que la decisión del autor de "todo de una vez" no se traduzca en "nada
demostrable si el tiempo se corta a mitad de camino".
**Regla para el futuro:** cuando dos specs se combinan, la pregunta no es "¿son
técnicamente compatibles los modelos de datos?" — casi siempre lo son con ajustes. La
pregunta es "¿siguen siendo una sola historia coherente de producto?" y "¿el scope
combinado tiene puntos de parada demostrables, o es todo-o-nada?". Ambas preguntas se
responden antes de escribir el SRS combinado, no durante la implementación.
**Tags:** #arquitectura #deuda-técnica

## [2026-08-04] — 22 tests en verde sobre una aplicación que nunca arrancó
**Contexto:** al construir el `docker-compose.yml` de la Sub-fase 0 y levantar el stack
por primera vez, el backend murió en el arranque: `AuthController` inyecta
`VerificationTokenRepository`, un puerto de dominio **sin ningún adaptador**. De los 8
puertos de `identity-service`, siete tenían implementación y ese tenía cero.
**Error cometido:** ningún test usaba `@SpringBootTest`. Los 22 tests instanciaban sus
colaboradores a mano, y el único llamado "integration" armaba
`MockMvcBuilders.standaloneSetup(...)`. Nadie cargaba el contexto de Spring, así que el
cableado de beans —que es justamente lo que la arquitectura hexagonal delega al
framework— no estaba verificado por nada.
**Consecuencia:** el SRS §16.1 declaraba el registro de tenants y la autenticación como
*"Delivered and verified today"*. Los tests eran reales y pasaban, pero verificaban
piezas sueltas; el sistema completo no podía iniciar. El defecto solo se hizo visible al
ejecutarlo, no al testearlo. Y como el reactor Maven también estaba roto por tres módulos
stub sin `<relativePath>`, `mvn` fallaba en la raíz — tapado por el `|| true` de CI. Dos
capas de señal falsa sobre el mismo hecho: nadie había corrido esto nunca.
**Corrección:** adaptador JPA escrito, y `ApplicationContextLoadsTest` agregado — un test
que solo levanta el contexto y falla si algún bean no resuelve. Un puerto sin adaptador,
un bean ambiguo o una dependencia circular ahora rompen en `mvn test`.
**Regla para el futuro:** una suite sin un solo test que cargue el contexto de la
aplicación no prueba que la aplicación exista. En hexagonal esto pesa el doble: cuanto
mejor se aíslan dominio y casos de uso de sus adaptadores, más fácil es que el cableado
—la parte que nadie testea porque "es del framework"— sea justo lo que está roto. El test
de arranque es barato, va primero, y es el que traduce "los tests pasan" a "el sistema
corre".
**Tags:** #testing #arquitectura

## [2026-08-04] — Un ADR con número duplicado no se puede citar
**Contexto:** `docs/adr/ADR-008.md` era "Infra: Railway/Supabase/Upstash/Confluent",
mientras el SRS §17 asignaba ADR-008 a "GDPR Erasure vs. Colombian Retention". Dos
documentos distintos, el mismo identificador. Aparte, `ADR-00X-jwt-management.md` nunca
recibió número, y el de infra seguía en estado "Propuesto" pese a que su premisa central
—"simplificar el desarrollo **sin Docker**"— había sido revertida por ADR-012.
**Error cometido:** numerar un ADR nuevo sin verificar qué números ya estaban tomados, y
no actualizar el estado de un ADR cuando una decisión posterior lo dejó sin efecto.
**Consecuencia:** la referencia "ADR-008" dejó de significar algo. Y peor: un lector que
abriera el ADR de infra encontraba "Propuesto" sobre una decisión muerta, con
instrucciones activas de desplegar en Railway y usar Kafka y Redis — todo contradicho por
el SRS vigente. El registro de decisiones estaba desinformando en vez de informando.
**Corrección:** el de infra pasa a ADR-016 y queda marcado **superado por ADR-012 y
ADR-015**, con el detalle de qué lo revirtió. El de JWT pasa a ADR-017. Ambos indexados
en el SRS §17. Ninguno se borra.
**Regla para el futuro:** un ADR superado se marca superado y se queda — ese es el punto
del registro. Pero "se queda" sin actualizar el estado es peor que borrarlo, porque un
documento que dice "Propuesto" se lee como vigente. El estado de un ADR es parte de su
contenido, no metadata decorativa: cuando una decisión nueva revierte una vieja, cerrar
la vieja es parte de tomar la nueva, no una tarea de limpieza posterior.
**Tags:** #arquitectura #deuda-técnica

## [2026-08-05] — `@ConditionalOnMissingBean` es por tipo: un bean propio apaga la autoconfiguración de "el otro" en silencio (tres veces seguidas, en el mismo cambio)
**Contexto:** Sub-fase 1 necesitaba dos roles de base de datos — uno administrador para
Flyway/`PostgresSchemaProvisioner`, uno restringido (`carelink_app`) para JPA y el resto
del tráfico, condición sin la cual AC-10 no es verificable (con un solo rol superusuario,
`REVOKE` no hace nada). La primera versión agregó un `@Bean DataSource adminDataSource()`
manual y asumió que Spring Boot seguiría autoconfigurando el primario a partir de
`spring.datasource.*`, como si el bean nuevo fuera un simple agregado.
**Error cometido, repetido tres veces sin que las primeras dos correcciones lo generalizaran:**
1. `DataSourceAutoConfiguration` crea su `DataSource` bajo
   `@ConditionalOnMissingBean(DataSource.class)` — una condición por TIPO, no por nombre.
   En cuanto existía cualquier bean `DataSource` en el contexto (el administrador), Spring
   Boot se abstenía de crear el primario. Resultado: JPA y todo el tráfico de la
   aplicación corrían con privilegios de superusuario.
2. La corrección obvia —`@ConfigurationProperties(prefix="spring.datasource")` sobre
   `DataSourceBuilder.create().build()`— bindea por reflexión sobre los setters del
   objeto ya construido, y `HikariDataSource` no tiene `setUrl(...)`, tiene
   `setJdbcUrl(...)`. La URL nunca llegaba al pool, y recién se notaba porque Hibernate
   fallaba con "Unable to determine Dialect without JDBC metadata" — un error que no
   menciona la palabra "rol" ni "URL" en ningún lado obvio.
3. Corregido eso con el patrón real de Spring Boot (bindear sobre `DataSourceProperties`,
   que sí tiene un campo `url`, y usar `initializeDataSourceBuilder()`), el MISMO patrón
   volvió a pasar un nivel más arriba: mi propio bean `adminJdbcTemplate` (tipo
   `JdbcTemplate`, que implementa `JdbcOperations`) hizo que
   `JdbcTemplateAutoConfiguration` — también `@ConditionalOnMissingBean(JdbcOperations.class)`
   — se abstuviera de crear el `jdbcTemplate` autoconfigurado. El único `JdbcTemplate` del
   contexto volvía a ser el administrador.
**Cómo se detectó cada capa:** NINGUNA la encontró un test hasta que se escribió uno a
propósito. `docker compose up` + `pg_stat_activity` mostró las 11 conexiones activas bajo
`usename=carelink` (admin) en vez de `carelink_app` — eso destapó la capa 1. Corregir esa
reveló la 2 (Hibernate fallando al arrancar). Corregir esa reveló la 3, ya en un test
nuevo (`ApplicationContextLoadsTest.primaryDataSourceConnectsAsRestrictedRoleNotAdmin`,
escrito específicamente para volver a comprobar esto) que seguía fallando con
`current_user = "postgres"` en vez de `carelink_app` — hizo falta un test diagnóstico
temporal (`ctx.getBeanNamesForType(...)`) para ver que solo existía UN bean `JdbcTemplate`
en todo el contexto.
**Consecuencia si no se hubiera destapado:** AC-10 —la garantía de que el rol de
aplicación no puede borrar `audit_log`— habría estado rota en el sistema real mientras
`AuditLogAppendOnlyIT` seguía en verde, porque ese test construye su `JdbcTemplate` a
mano con credenciales explícitas y nunca pasa por el cableado real de Spring. Exactamente
el patrón de toda esta sesión: cobertura que parece existir y no prueba lo que hace falta.
**Corrección final:** `DataSourceConfig` define los DOS `DataSource` Y los DOS
`JdbcTemplate` explícitamente — nunca uno manual y el otro "que lo arme Spring". Se agregó
`ApplicationContextLoadsTest.primaryDataSourceConnectsAsRestrictedRoleNotAdmin`, que
verifica con `SELECT current_user` contra el bean primario TAL CUAL lo arma Spring —no
uno armado a mano en el test— que efectivamente es `carelink_app`.
**Regla para el futuro:** en cuanto se define un bean propio de un tipo que Spring Boot
autoconfigura condicionalmente (`DataSource`, `JdbcTemplate`, `TransactionManager`,
cualquiera con `@ConditionalOnMissingBean` en su autoconfiguración), hay que asumir la
propiedad de TODOS los beans de ese tipo en el contexto, no solo el nuevo. "Dejo que
Spring siga autoconfigurando el otro" es la suposición que se rompe en silencio — sin
excepción, sin log, solo un bean que termina apuntando a donde no debía. Y la única forma
confiable de comprobar que el bean primario "tal cual lo arma Spring" hace lo que se
espera es ejercitarlo con una aserción de runtime (`SELECT current_user`, no una lectura
del código) — un test unitario que construye sus propios colaboradores no puede detectar
un problema de cableado que solo existe cuando Spring arma el grafo de beans completo.
**Tags:** #arquitectura #seguridad #testing

## [2026-08-05] — Mover un paquete a su "casa" documentada rompió el component-scan en TODO entorno, no solo en tests (segunda ocurrencia de la misma clase de bug que `DataSourceConfig`)
**Contexto:** `EncryptionService`/`AesGcmEncryptionService` vivían en `com.carelink.identity.*`
por comodidad de cuándo se escribieron; `copilot-instructions.md` ya documentaba
`com.carelink.clinical.*` como su paquete destino. Se movieron con `git mv` para alinear
con la estructura documentada — un cambio que se sentía puramente cosmético.
**Error cometido:** `com.carelink.clinical` es HERMANO de `com.carelink.identity` (donde
vive `Application.java`), no un hijo. `@SpringBootApplication` sin `scanBasePackages`
explícito solo escanea hacia abajo desde su propio paquete — nunca hacia los costados.
Mover el paquete sacó a TODOS los beans de `clinical` (no solo los movidos, sino
`RegisterPatientUseCase`, `GetPatientUseCase`, todo lo que ya estaba ahí) del
component-scan, en la app real corriendo con `docker compose up` tanto como en cualquier
test — no era un artefacto de configuración de test.
**Cómo se detectó:** el primer test de `PatientLifecycleIT` (que ya estaba en el
paquete `clinical`) falló con "No qualifying bean of type ... RegisterPatientUseCase" —
no un error de cifrado, un error de que Spring nunca había visto la clase.
**Corrección:** `scanBasePackages = {"com.carelink.identity", "com.carelink.clinical"}`
explícito en `Application.java`, con un javadoc explicando por qué hace falta.
**Por qué es la misma lección que `DataSourceConfig` (ver entrada anterior) aunque el
mecanismo sea distinto:** en ambos casos, un cambio que se ve prolijo y correcto por
inspección de código (mover una clase a su paquete "correcto"; agregar un bean que
reemplaza a otro) tiene una consecuencia de runtime que el código en sí no revela —
Spring decide en silencio, sin log ni excepción, dejar de hacer algo que antes hacía.
Ninguna de las dos veces la encontró la lectura del diff; las dos veces la encontró
correr el sistema real (test de integración con contexto completo, o `docker compose up`).
**Regla para el futuro:** cualquier cambio de paquete o de bean en un proyecto Spring que
no se prueba de inmediato contra un `@SpringBootTest` de contexto completo (o el stack
real) es una apuesta. La estructura "documentada" en un `.md` no es la misma garantía que
un test que arranca el contexto y falla si algo no se resuelve.
**Tags:** #arquitectura #spring #testing

## [2026-08-05] — El scope `runtime` de una dependencia es real en compilación, no solo en el jar final
**Contexto:** `JdbcClinicalEncounterRepository.update(...)` necesitaba distinguir el
rechazo del trigger de inmutabilidad (SQLSTATE `P0409`) de cualquier otro error de base
de datos, para traducirlo a `EncounterAlreadySignedException` en vez de dejarlo
propagarse como un 500 genérico.
**Error cometido:** el primer intento importó `org.postgresql.util.PSQLException`, el
tipo obvio para leer `getSQLState()` en un proyecto que usa PostgreSQL en todos lados —
no compiló. `org.postgresql:postgresql` tiene `<scope>runtime</scope>` en el `pom.xml`
del proyecto (deliberado: nada en el código de aplicación debería depender del driver
específico), así que la clase simplemente no está en el classpath de compilación,
aunque sí lo esté en tests y en el jar final — el error solo aparece al compilar código
nuevo que la referencia, no al correr nada existente.
**Corrección:** `java.sql.SQLException` (API JDBC estándar) también expone
`getSQLState()` — es lo que hay que usar cuando lo único que hace falta es leer el
SQLSTATE, no ninguna otra funcionalidad específica del driver de Postgres.
**Regla para el futuro:** antes de importar una clase de `org.postgresql.*` en código de
`main` (no de test), preguntar si la API JDBC estándar (`java.sql.*`) ya cubre lo que
hace falta — casi siempre sí para lectura de errores (`SQLException.getSQLState()`,
`getErrorCode()`). El scope `runtime` del driver en el pom es una decisión de diseño
existente, no un accidente a rodear con un import puntual.
**Tags:** #java #build

## [2026-08-05] — Un flujo completo de sub-fase se armó sobre un actor (`PHYSICIAN`) que el sistema no puede crear
**Contexto:** al verificar `ClinicalEncounterController` (que exige rol `PHYSICIAN` para
registrar/editar/firmar) contra `docker compose up`, hizo falta un JWT con ese rol para
probar el camino feliz, no solo el 403 de rol incorrecto.
**Error cometido / gap encontrado:** no existe ningún flujo — ni endpoint, ni caso de
uso — para crear un usuario con un rol distinto de `TENANT_ADMIN`. El registro de tenant
(`RegisterTenantUseCase`) crea exactamente un usuario, siempre `TENANT_ADMIN`. FR-ID-02
(invitación de usuarios con asignación de rol) nunca se construyó, y nada en Sub-fase 0
o 1 lo hubiera revelado porque ninguna de esas dos sub-fases necesitaba un segundo rol
para demostrarse.
**Cómo se resolvió puntualmente (sin tratarlo como arreglado):** `UPDATE users SET
role='PHYSICIAN' ...` directo contra la base del contenedor corriendo, solo para poder
completar esta verificación puntual — explícitamente narrado como un atajo de test, no
como algo que el producto en sí puede hacer.
**Por qué importa más allá de este ítem:** Sub-fases 3 a 6 dependen de roles que hoy son
igual de inalcanzables (`NURSE`, `SPECIALIST`, `LAB_TECH`, `PHARMACIST`, `ADMISSIONS`).
Sin resolver esto, la verificación en vivo de esas sub-fases se degrada al mismo atajo de
SQL directo, que no prueba nada sobre el producto real — sería repetir el mismo problema
que esta sesión evitó activamente en Sub-fase 1 y 2 (no confiar en verificación que no
pasa por el sistema real).
**Regla para el futuro:** construir FR-ID-02 no estaba en el alcance de ningún ítem de
Sub-fase 2 planeado — no se resolvió en silencio ampliando el alcance; se documentó como
gap explícito en `docs/SRS.md` y `tasks/todo.md`, a la espera de decidir si se prioriza
antes de continuar a Sub-fase 3, o si Sub-fase 3+ tolera el mismo atajo de verificación
un poco más.
**Tags:** #alcance #identity #testing

## [2026-08-05] — Un test suite en verde nunca ejercitó `SmtpEmailNotifier` de verdad; el registro de tenant estaba roto contra un `docker compose up` fresco
**Contexto:** al construir FR-ID-02 (invitación de usuarios), el flujo reutiliza el
mismo patrón de "generar token, enviarlo por email" que `RegisterTenantUseCase` ya usa
para la verificación de email al registrar un tenant. Antes de construir sobre ese
patrón, se decidió probarlo en vivo contra `docker compose up` para confirmar que
funcionaba de verdad — la misma disciplina de "correr el sistema real, no confiar solo
en tests en verde" ya aplicada el resto de la sesión.
**Lo que se encontró:** el registro de un tenant NUEVO fallaba con
`MailSendException: Connection refused` — `SmtpEmailNotifier` intenta una conexión SMTP
real, `docker-compose.yml` nunca pasaba `SMTP_HOST`/`SMTP_PORT` al backend (caía al
default de Spring, `localhost:1025`, que dentro del contenedor del backend es el propio
backend, no un servidor de correo), y no existía ningún contenedor de correo en el
compose. `.env.example` ya documentaba la intención ("apuntar a un catcher local,
MailHog/Mailpit") pero nadie había agregado el contenedor.
**Por qué ningún test lo detectó:** cada test que pasa por `RegisterTenantUseCase` con
el contexto completo de Spring (`AuthControllerSecurityIT`) usa `@MockBean` sobre
`EmailNotifier` — nunca ejercita `SmtpEmailNotifier` de verdad. Los tests unitarios
(`RegisterTenantUseCaseTest`) usan un fake en memoria. Ningún test, en ningún nivel,
levantaba un servidor SMTP real ni verificaba que la app pudiera conectar a uno. El
`docker compose up` es la única capa que ejercita el bean real — y nadie lo había
vuelto a correr desde cero (base de datos limpia, sin tenants ya registrados) desde que
se agregó `MailHealthIndicator` deshabilitado (que enmascaró la ausencia de SMTP como
"no hay nada que healthcheckear", no como "esto se rompe si alguien lo usa").
**Corrección:** se agregó `axllent/mailpit` a `docker-compose.yml` (imagen liviana,
API REST para leer los mensajes capturados — útil para verificar en vivo sin abrir un
navegador) y se wireó `SMTP_HOST=mailpit`/`SMTP_PORT=1025` explícito en el entorno del
backend. Nada sale de la red local de compose — consistente con §16.4 (sin
integración SMTP viva). Reverificado: registro de tenant fresco → 201, Mailpit recibe
el email real con el token de verificación.
**Regla para el futuro:** un puerto con una sola implementación real (`EmailNotifier` →
`SmtpEmailNotifier`, sin alternativa en este milestone) que TODOS los tests reemplazan
por un doble es un puerto que nunca se ejercita de verdad en ningún nivel de test —
solo `docker compose up` desde cero lo prueba. Cuando ese patrón se detecta, vale la
pena levantar el stack completo y probar el flujo real ANTES de construir algo nuevo
que dependa del mismo mecanismo, no después.
**Tags:** #infraestructura #docker-compose #testing

## [2026-08-05] — FR-ID-02: construido recién con confirmación explícita del usuario, no por iniciativa propia
**Contexto:** al cerrar Sub-fase 2, se encontró que FR-ID-02 (invitación de usuarios
con asignación de rol) nunca se había construido — ver la entrada anterior sobre el
gap encontrado durante `ClinicalEncounterController`. Antes de construirlo, se le
preguntó explícitamente al usuario cómo seguir (construirlo ahora, seguir con el atajo
de SQL, o pausar a revisar el alcance primero) en vez de decidir unilateralmente,
seguir la regla del proyecto de "si una tarea obliga a tocar algo fuera del alcance de
la sub-fase activa, parar y preguntar antes de tocarlo".
**Confirmado:** el usuario eligió "construir FR-ID-02 ahora". A partir de esa
confirmación explícita se construyó el flujo completo (invitar, aceptar invitación,
desactivar) — documentado en la entrada de Sub-fase 2 de `tasks/todo.md` y en
`docs/SRS.md` §5.1.
**Por qué vale la pena registrar esto como lección y no solo como una tarea más:** es
un caso limpio del patrón que el usuario pidió seguir desde el primer mensaje de la
sesión — nombrar la decisión de alcance no cubierta y esperar confirmación, en vez de
resolverla en silencio — funcionando como se pretendía, sin fricción, con una sola
pregunta bien encuadrada (tres opciones concretas, no abierta). Vale la pena seguir
haciéndolo así cuando aparezca la próxima decisión de alcance no cubierta (candidato ya
identificado: si Sub-fase 3+ necesita más roles gateados, la misma pregunta va a volver
a aparecer).
**Tags:** #alcance #proceso

## [2026-08-05] — `mvn test-compile` sin `clean` dio BUILD SUCCESS con clases obsoletas, sobre código que no compilaba
**Contexto:** al implementar AC-06b se cambiaron las firmas de siete casos de uso
(agregar `ServiceScope`) y de tres records de dominio (agregar `serviceId`). Después de
tocar todo eso, `mvn -q test-compile` devolvió BUILD SUCCESS.
**Error:** era falso. Los tests seguían llamando a las firmas viejas —
`getPatientUseCase.execute(tenantSlug, id)` con dos argumentos donde ahora hacían falta
tres— y no compilaban. El compilador incremental de Maven comparó timestamps de
`.java` contra `.class` y decidió que no hacía falta recompilar los tests, así que
validó contra las clases YA COMPILADAS de la corrida anterior, no contra el código
fuente nuevo. `mvn clean test-compile` destapó 22 errores de compilación de golpe.
**Cómo se detectó:** por desconfianza, no por una falla. El BUILD SUCCESS era
sospechoso porque un `grep` sobre `src/test` mostraba llamadas con la cantidad de
argumentos vieja — dos hechos que no podían ser ciertos a la vez. Sin ese chequeo, el
siguiente paso habría sido correr los tests, que habrían corrido los `.class` viejos
contra el `main` nuevo o fallado de una forma mucho más confusa.
**Regla para el futuro:** después de un cambio de FIRMA que cruza el límite
main/test (no un cambio de cuerpo de método), `mvn clean` no es opcional — el
compilador incremental razona por timestamps, no por compatibilidad de API, y un
BUILD SUCCESS suyo no significa "el código fuente actual compila". Es la misma familia
de problema que el resto de esta sesión: una señal verde que no verifica lo que uno
supone que verifica (los tests que nunca corrieron por falta de Failsafe, el suite en
verde sobre una app que no arrancaba).
**Tags:** #build #testing

## [2026-08-06] — Un gate que busca un literal solo detecta ese literal: el secreto por defecto volvió con otro nombre
**Contexto:** la auditoría de Sub-fase 8 encontró que `TokenHasher` hacía
`getenv().getOrDefault("REFRESH_TOKEN_HMAC_SECRET", "dev-refresh-secret")`. Sin la
variable de entorno, la aplicación arrancaba normalmente y hasheaba TODOS los refresh
tokens con un secreto escrito en el repositorio — el control de "guardamos hasheado, no
en claro" seguía existiendo en apariencia y no protegía nada.
**Error cometido:** es EXACTAMENTE el mismo defecto que ADR-010 eliminó en Sub-fase 0
(el fallback `dev-secret` del gateway Python), reaparecido en otro archivo, con otro
nombre. Y el gate de CI que se escribió para que eso no volviera —`grep "HS256\|dev-secret"`—
no lo detectó, porque `dev-refresh-secret` no contiene `dev-secret` como substring.
**Consecuencia:** el defecto convivió con un gate diseñado específicamente para
prevenirlo, durante ocho sub-fases, con CI en verde todo el tiempo. Gitleaks tampoco lo
marca: es un literal con forma de identificador, no un patrón de secreto.
**Corrección:** (a) la aplicación ya no arranca sin el secreto, mismo criterio que
`CLINIC_ENCRYPTION_KEY`; (b) `RefreshSecretGuard` adelanta la validación al arranque,
porque si no recién dispararía en el primer login —y hasta entonces `/actuator/health`
diría UP; (c) el gate nuevo busca el PATRÓN estructural (`getOrDefault` sobre una
variable con nombre de secreto) en vez de un literal.
**Regla para el futuro:** un gate escrito contra una instancia concreta de un defecto
detecta esa instancia, no la clase. Cuando se cierra un agujero con un `grep`, la
pregunta a hacerse es "¿qué FORMA tiene este defecto?" y no "¿qué texto tenía esta vez?".
El texto cambia con el próximo desarrollador que lo reintroduzca sin saber que existió.
**Tags:** #seguridad #testing #ci

## [2026-08-06] — Una regla de SAST en verde sobre código que nunca miró
**Contexto:** `.semgrep/no-string-sql.yaml` tenía un único patrón,
`String sql = "..." + $X;`, y estaba en verde desde Sub-fase 0.
**Error cometido:** ese patrón cubre una forma de escribir SQL que este codebase NO usa.
Los nueve repositorios con SQL dinámico arman la consulta con `StringBuilder` o la pasan
directo a `jdbcTemplate.query(...)`. La regla nunca había evaluado una sola línea del
código que supuestamente vigilaba.
**Segundo error, encontrado al corregir el primero:** la primera versión ampliada usaba
`$T.query("..." + $X, ...)` y TAMPOCO detectaba la inyección clásica. En Java,
`"SELECT ... '" + userInput + "'"` se parsea como `(("SELECT ... '" + userInput) + "'")`,
así que un patrón anclado al literal de la izquierda no matchea el nodo de más afuera.
Se descubrió porque se escribió un archivo deliberadamente vulnerable ANTES de confiar
en la regla, y la regla lo dejó pasar.
**Corrección:** patrones sobre `$A + $B` (la concatenación más externa, sin importar el
anidamiento), más excepciones acotadas por nombre de identificador para los dos casos
auditados de este repositorio (`schema`, `sql + "literal"`). Verificación final en las
dos direcciones: 3 hallazgos sobre el archivo vulnerable, 0 sobre el código real.
**Regla para el futuro:** una regla de SAST se valida contra código que DEBE detectar,
no contra código limpio. "0 hallazgos" sin un caso positivo que la ejercite es
indistinguible de "la regla no matchea nada" — el mismo problema del `|| true` y de los
tests que nunca corrieron por falta de Failsafe, con otra forma.
**Tags:** #seguridad #testing #sast
