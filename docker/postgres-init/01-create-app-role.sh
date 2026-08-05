#!/bin/sh
set -eu

# Crea el rol restringido que usa la aplicación en tiempo de ejecución (AC-10).
#
# Corre una sola vez, cuando Postgres inicializa su volumen de datos por primera
# vez — antes de que el contenedor del backend exista siquiera. Esto no es
# arbitrario: si este rol se creara desde una migración de Flyway (que corre
# DESPUÉS de que el backend arranca), habría una carrera entre "el rol de
# aplicación existe" y "el pool de conexiones de la aplicación intenta abrir su
# primera conexión con ese rol" — y en Spring Boot ese pool puede intentar
# conectar antes de que Flyway termine. Correrlo acá elimina la carrera en vez
# de intentar ganarla con orden de beans.
#
# La contraseña llega por variable de entorno, nunca queda en un archivo
# versionado — mismo criterio que cualquier otro secreto del proyecto.
#
# El rol NO tiene CREATEDB, CREATEROLE ni SUPERUSER (son los defaults de
# CREATE ROLE; se listan acá para que la ausencia sea legible, no asumida). No
# puede crear ni alterar tablas — todo el DDL, incluidos los GRANT que le dan
# acceso a las tablas que sí puede usar, lo hace el rol administrador
# ($POSTGRES_USER) desde Flyway y desde PostgresSchemaProvisioner.

: "${CARELINK_APP_DB_USER:?falta CARELINK_APP_DB_USER}"
: "${CARELINK_APP_DB_PASSWORD:?falta CARELINK_APP_DB_PASSWORD}"

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<-EOSQL
    DO \$\$
    BEGIN
        IF NOT EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = '${CARELINK_APP_DB_USER}') THEN
            CREATE ROLE "${CARELINK_APP_DB_USER}" LOGIN PASSWORD '${CARELINK_APP_DB_PASSWORD}';
        ELSE
            ALTER ROLE "${CARELINK_APP_DB_USER}" WITH LOGIN PASSWORD '${CARELINK_APP_DB_PASSWORD}';
        END IF;
    END
    \$\$;

    GRANT CONNECT ON DATABASE "$POSTGRES_DB" TO "${CARELINK_APP_DB_USER}";
    GRANT USAGE ON SCHEMA public TO "${CARELINK_APP_DB_USER}";
EOSQL
