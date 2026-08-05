-- Plantilla de schema de tenant. La ejecuta PostgresSchemaProvisioner con
-- search_path apuntando a `tenant_<slug>`, así que las referencias sin calificar
-- de acá abajo resuelven contra el schema del tenant que se está provisionando.

-- Patient (FR-CLN-01). Primer corte, no el formulario de admisión completo —
-- contacto, contacto de emergencia, medicación activa y afiliación EPS/SISBEN
-- quedan para después (ver el javadoc de Patient.java).
--
-- Las columnas PHI son TEXT aunque su tipo lógico no lo sea (date_of_birth es
-- una fecha, no texto) porque lo que se guarda es base64(IV + ciphertext) —
-- JdbcPatientRepository cifra antes de escribir y descifra al leer.
-- document_type, sex y blood_type NO se cifran: son categóricos, no
-- identifican por sí solos (igual criterio que `role` en la tabla `users`).
CREATE TABLE IF NOT EXISTS patients (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    full_name        TEXT        NOT NULL, -- cifrado
    document_type    TEXT        NOT NULL,
    document_number  TEXT        NOT NULL, -- cifrado
    date_of_birth    TEXT        NOT NULL, -- cifrado
    sex              TEXT        NOT NULL,
    blood_type       TEXT        NOT NULL,
    allergies        TEXT,                 -- cifrado, JSON serializado
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now()
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

-- SELECT, INSERT nada más — todavía no hay un caso de uso que edite o borre un
-- paciente para decidir esas reglas a ciegas (UPDATE/DELETE se agregan cuando
-- exista uno, no antes).
GRANT SELECT, INSERT ON patients TO {{app_role}};

-- ClinicalEncounter (FR-CLN-02). Igual criterio de cifrado que patients:
-- chief_complaint/exam_findings/treatment_plan/follow_up son notas clínicas de
-- texto libre — PHI, cifradas. diagnosis_cie10 es un código estructurado, no
-- texto libre, y el Motor de Conocimiento (Sub-fase 4) necesita poder agruparlo
-- sin descifrar cada fila — mismo criterio que document_type/sex/blood_type en
-- patients: categórico, no identifica por sí solo.
--
-- Inmutabilidad tras la firma, en DOS capas — mismo patrón que audit_log:
--   1. El trigger de abajo bloquea CUALQUIER UPDATE sobre una fila ya firmada
--      (OLD.signed_at IS NOT NULL), para cualquier rol, incluido el admin. Esta
--      es la garantía que Ley 527/1999 y Res. 1995/1999 piden — a nivel de base,
--      no de lógica de aplicación que alguien con acceso directo podría saltear.
--   2. La aplicación nunca intenta ese UPDATE en el camino normal: firmar es un
--      UPDATE separado con `WHERE signed_at IS NULL` (JdbcClinicalEncounterRepository),
--      así que un intento de re-firmar no afecta filas en vez de disparar el
--      trigger — mismo resultado (rechazado), disparado por el guard correcto
--      según el caso.
-- El código de error P0409 (elegido, no un estándar de Postgres) es lo que el
-- adaptador de Java usa para distinguir "está firmado" de cualquier otro fallo
-- de base de datos y traducirlo a 409, no a un 500 genérico.
CREATE TABLE clinical_encounters (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    patient_id         UUID        NOT NULL,
    physician_user_id  UUID        NOT NULL,
    chief_complaint    TEXT        NOT NULL, -- cifrado
    exam_findings      TEXT,                 -- cifrado
    diagnosis_cie10    TEXT,
    treatment_plan     TEXT,                 -- cifrado
    follow_up          TEXT,                 -- cifrado
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    signed_at          TIMESTAMPTZ,
    signed_by_user_id  UUID
);

CREATE INDEX idx_clinical_encounters_patient_id ON clinical_encounters (patient_id);

CREATE OR REPLACE FUNCTION clinical_encounter_block_signed_mutation()
RETURNS TRIGGER AS $encounter_guard$
BEGIN
    IF OLD.signed_at IS NOT NULL THEN
        RAISE EXCEPTION 'clinical_encounter % ya está firmado, es inmutable (Ley 527/1999, Res. 1995/1999, FR-CLN-02)', OLD.id
            USING ERRCODE = 'P0409';
    END IF;
    RETURN NEW;
END;
$encounter_guard$ LANGUAGE plpgsql;

CREATE TRIGGER clinical_encounter_no_update_when_signed
    BEFORE UPDATE ON clinical_encounters
    FOR EACH ROW EXECUTE FUNCTION clinical_encounter_block_signed_mutation();

-- Sin DELETE: un encounter, firmado o no, no se borra — es historia clínica.
GRANT SELECT, INSERT, UPDATE ON clinical_encounters TO {{app_role}};

-- Admission (FR-CLN-03). Sin cifrado — a diferencia de patients/clinical_encounters,
-- no hay ningún campo de texto libre con PHI acá: admission_type y triage_priority son
-- categóricos (mismo criterio que role/diagnosis_cie10). triage_priority es NULL para
-- CONSULTA_EXTERNA — Triage Manchester es una herramienta de urgencias; esa regla la
-- impone RegisterAdmissionUseCase, no una constraint acá, mismo criterio que el resto
-- de las reglas de negocio de este proyecto (ver TenantSlug/DataSourceConfig: las
-- constraints de base son para invariantes de seguridad adversarial, no reglas de
-- negocio ordinarias).
--
-- clinical_encounter_id es NULL hasta que se abre un encounter para esta admisión —
-- LinkEncounterToAdmissionUseCase lo completa con un UPDATE, por eso el GRANT incluye
-- UPDATE además de SELECT/INSERT.
CREATE TABLE admissions (
    id                     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    patient_id             UUID        NOT NULL,
    admission_type         TEXT        NOT NULL,
    triage_priority        INTEGER,
    admitted_by_user_id    UUID        NOT NULL,
    admitted_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    clinical_encounter_id  UUID,
    created_at             TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_admissions_patient_id ON admissions (patient_id);

GRANT SELECT, INSERT, UPDATE ON admissions TO {{app_role}};
