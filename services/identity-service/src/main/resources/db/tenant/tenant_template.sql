-- Plantilla de schema de tenant. La ejecuta PostgresSchemaProvisioner con
-- search_path apuntando a `tenant_<slug>`, así que las referencias sin calificar
-- de acá abajo resuelven contra el schema del tenant que se está provisionando.

-- Placeholder de la Sub-fase 0/1, sin cifrar. La Sub-fase 2 no la extiende: la
-- reemplaza por la entidad Patient real (documento, tipo de sangre, alergias,
-- afiliación EPS/SISBEN — FR-CLN-01) con las columnas PHI pasadas por
-- EncryptionService (AES-256-GCM, AC-09). Ninguna columna que se agregue a ESTA
-- tabla debe llevar PHI sin cifrar primero.
CREATE TABLE IF NOT EXISTS patients (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    full_name TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT now()
);

-- audit_log (FR-CLN-13, SRS §10) — append-only, por tenant. Cada lectura,
-- escritura o export de PHI genera una fila acá: timestamp, usuario, rol,
-- paciente, acción, service_id, IP de origen, ID de sesión.
--
-- "Append-only" se cumple en DOS capas independientes, no una:
--   1. El trigger de abajo bloquea UPDATE y DELETE para CUALQUIER rol que
--      conecte a esta base, incluido el administrador. Es la garantía de que ni
--      un bug de aplicación ni un acceso administrativo alteran el historial.
--   2. El GRANT al final restringe además al rol de aplicación específicamente
--      a INSERT y SELECT — para que el camino normal de la aplicación ni
--      siquiera intente violar el trigger (AC-10).
-- Cada capa cubre lo que la otra no: el trigger no depende de qué rol conecta,
-- el GRANT no depende de que el trigger esté bien escrito.
CREATE TABLE audit_log (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    occurred_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    user_id      UUID,
    role         TEXT,
    patient_id   UUID,
    action       TEXT        NOT NULL,
    service_id   TEXT,
    source_ip    TEXT,
    session_id   UUID,
    result       TEXT        NOT NULL DEFAULT 'SUCCESS',
    detail       JSONB
);

CREATE INDEX idx_audit_log_patient_id  ON audit_log (patient_id);
CREATE INDEX idx_audit_log_occurred_at ON audit_log (occurred_at);

CREATE OR REPLACE FUNCTION audit_log_block_mutation()
RETURNS TRIGGER AS $audit_log_guard$
BEGIN
    RAISE EXCEPTION 'audit_log es append-only: % no está permitido (SRS §5.10, FR-CLN-13)', TG_OP;
END;
$audit_log_guard$ LANGUAGE plpgsql;

CREATE TRIGGER audit_log_no_update
    BEFORE UPDATE ON audit_log
    FOR EACH ROW EXECUTE FUNCTION audit_log_block_mutation();

CREATE TRIGGER audit_log_no_delete
    BEFORE DELETE ON audit_log
    FOR EACH ROW EXECUTE FUNCTION audit_log_block_mutation();

-- {{app_role}} lo reemplaza PostgresSchemaProvisioner en Java antes de ejecutar
-- este archivo, con el mismo valor que spring.datasource.username — una sola
-- fuente de verdad para "cuál es el rol de aplicación", no un literal
-- duplicado en cada lugar que lo necesita. El valor viene de configuración de
-- despliegue (env var), no de entrada de un usuario en runtime.
GRANT SELECT, INSERT ON audit_log TO {{app_role}};

-- SELECT, INSERT nada más: `patients` sigue siendo el placeholder de arriba, no
-- la entidad Patient real (próximo paso de Sub-fase 2). UPDATE/DELETE se
-- definen junto con el modelo real, no antes — no hay todavía un caso de uso
-- que edite o borre un paciente para decidir esas reglas a ciegas.
GRANT SELECT, INSERT ON patients TO {{app_role}};
