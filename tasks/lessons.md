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