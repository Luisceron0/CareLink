-- V3 — Otorga al rol de aplicación acceso a las tablas de Identity.
--
-- El rol en sí (${app_db_user}) ya existe cuando esta migración corre: lo crea
-- docker/postgres-init/01-create-app-role.sh al inicializar el volumen de datos
-- de Postgres, ANTES de que el backend —y por lo tanto Flyway— arranque
-- siquiera. Si el rol se creara acá en lugar de ahí, habría una carrera entre
-- "el rol existe" y "el pool de conexiones de la aplicación, que usa ese rol,
-- intenta abrir su primera conexión" — HikariCP puede intentar conectar antes de
-- que Flyway termine de correr. Esta migración solo otorga permisos sobre
-- objetos que ya existen (rol y tablas); no crea nada nuevo.
--
-- ${app_db_user} es un placeholder de Flyway (spring.flyway.placeholders,
-- application.yml), no un literal — la misma fuente que define con qué usuario
-- corre docker-entrypoint-initdb.d.

-- CRUD completo sobre las tablas propias de Identity: no son append-only.
-- FR-ID-02 pide retener el historial de auditoría al desactivar un usuario, no
-- la fila del usuario en sí — eso lo decide la aplicación (desactivar, no
-- borrar), no un permiso de base de datos.
GRANT SELECT, INSERT, UPDATE, DELETE ON tenants, users, sessions, verification_tokens
    TO ${app_db_user};

-- Solo lectura sobre el sello de contención. La aplicación tiene que poder
-- verificarlo (DemoModeGuard, AC-02) pero no tiene ningún motivo para
-- escribirlo — es un valor fijo que pone el rol administrador al migrar (V2).
GRANT SELECT ON containment_marker TO ${app_db_user};
