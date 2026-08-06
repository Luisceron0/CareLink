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
    -- AC-06b: servicio (departamento) al que pertenece este paciente. Se estampa con
    -- el service_id del usuario que lo registra y filtra toda lectura posterior de
    -- roles no exentos. NULL solo si lo creó un rol exento (TENANT_ADMIN) — ver
    -- AuthenticatedPrincipal.serviceScopeFilter(). Categórico, no se cifra.
    service_id       TEXT,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_patients_service_id ON patients (service_id);

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
    service_id         TEXT,                 -- AC-06b, mismo criterio que patients.service_id
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
    service_id             TEXT,       -- AC-06b, mismo criterio que patients.service_id
    created_at             TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_admissions_patient_id ON admissions (patient_id);

GRANT SELECT, INSERT, UPDATE ON admissions TO {{app_role}};

-- ============================================================================
-- Sub-fase 4 — Diario de enfermería (FR-CLN-04, FR-CLN-05) y el sustrato del
-- Motor de Conocimiento (FR-CLN-06, FR-CLN-07).
-- ============================================================================

-- HealthDiaryEntry. Vinculado a Patient + fecha/turno, NO a un ClinicalEncounter
-- abierto: §10 es explícito en que el seguimiento de enfermería puede abarcar toda
-- la admisión, independiente de los límites de un encounter.
--
-- `observations` es texto libre de enfermería — PHI, cifrado, mismo criterio que las
-- notas clínicas del encounter. `shift` y `entry_date` son categóricos/estructurales.
CREATE TABLE health_diary_entries (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    patient_id     UUID        NOT NULL,
    nurse_user_id  UUID        NOT NULL,
    entry_date     DATE        NOT NULL,
    shift          TEXT        NOT NULL,
    observations   TEXT,                 -- cifrado
    service_id     TEXT,                 -- AC-06b
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_health_diary_entries_patient_id ON health_diary_entries (patient_id);

-- VitalSigns. Los valores numéricos NO se cifran: son mediciones, no identificadores.
-- Una presión de 120/80 no identifica a nadie por sí sola (mismo criterio que
-- blood_type en patients), y dejarlos numéricos permite que un rango de referencia o
-- una alerta futura los evalúe en SQL sin descifrar cada fila.
CREATE TABLE vital_signs (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    diary_entry_id     UUID        NOT NULL REFERENCES health_diary_entries(id) ON DELETE RESTRICT,
    systolic_mmhg      INTEGER,
    diastolic_mmhg     INTEGER,
    heart_rate_bpm     INTEGER,
    respiratory_rate   INTEGER,
    temperature_c      NUMERIC(4,1),
    oxygen_saturation  INTEGER,
    recorded_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_vital_signs_diary_entry_id ON vital_signs (diary_entry_id);

-- HealthIntervention (NIC) + su InterventionOutcome (NOC).
--
-- El outcome vive en ESTA tabla y no en una propia, aunque §10 lo modele como entidad
-- aparte, por una razón concreta: §10 dice "has one" (uno a uno), siempre se consultan
-- juntos, y ADR-006/§10.1 piden un índice compuesto sobre
-- (diagnosis_code, nic_code, effectiveness) — que no puede existir como un solo índice
-- si esas columnas viven en dos tablas distintas. Partirlo en dos índices sobre dos
-- tablas cambiaría el plan de ejecución que ADR-006 dimensionó para <2s con 50k
-- entradas. El dominio Java sigue teniendo `InterventionOutcome` como su propio record
-- anidado dentro de HealthIntervention: la fusión es de almacenamiento, no de modelo.
--
-- diagnosis_cie10 y nanda_code se denormalizan acá (además de vivir en
-- clinical_encounters) porque el Motor de Conocimiento agrupa POR ellos: sacarlos por
-- JOIN contra los encounters del paciente ataría cada intervención a un encounter
-- abierto, exactamente el vínculo que FR-CLN-04 dice que NO existe.
--
-- effectiveness 1-5, constraint de base y no solo validación de aplicación: alimenta
-- directamente el Motor de Conocimiento (FR-CLN-05), y un valor fuera de rango
-- corrompería agregados que después se leen como evidencia clínica.
CREATE TABLE health_interventions (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    diary_entry_id        UUID        NOT NULL REFERENCES health_diary_entries(id) ON DELETE RESTRICT,
    patient_id            UUID        NOT NULL,
    nanda_code            TEXT,
    nic_code              TEXT        NOT NULL,
    diagnosis_cie10       TEXT,
    description           TEXT,                 -- cifrado
    performed_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- InterventionOutcome (FR-CLN-05), NULL hasta que se registra el resultado.
    noc_code              TEXT,
    effectiveness         INTEGER,
    outcome_notes         TEXT,                 -- cifrado
    outcome_recorded_at   TIMESTAMPTZ,
    service_id            TEXT,                 -- AC-06b

    CONSTRAINT health_interventions_effectiveness_range
        CHECK (effectiveness IS NULL OR (effectiveness BETWEEN 1 AND 5))
);

-- ADR-006 / §10.1: el índice compuesto que sostiene la consulta del Motor de
-- Conocimiento sin necesidad de una vista materializada.
CREATE INDEX idx_health_interventions_knowledge
    ON health_interventions (diagnosis_cie10, nic_code, effectiveness);
CREATE INDEX idx_health_interventions_patient_id ON health_interventions (patient_id);

GRANT SELECT, INSERT, UPDATE ON health_diary_entries TO {{app_role}};
GRANT SELECT, INSERT ON vital_signs TO {{app_role}};
-- UPDATE sobre health_interventions: registrar el outcome (FR-CLN-05) es un UPDATE
-- sobre la intervención ya creada, no una fila nueva.
GRANT SELECT, INSERT, UPDATE ON health_interventions TO {{app_role}};

-- ============================================================================
-- Sub-fase 5 — Interconsultas (FR-CLN-08, FR-CLN-09, FR-CLN-10).
-- ============================================================================

-- InterconsultationRequest. El acceso del especialista al paciente se DERIVA del
-- estado de esta fila en cada request (FR-CLN-10): no hay ninguna tabla de
-- "permisos concedidos" que mantener sincronizada, ni un flag "tiene acceso" que
-- pueda quedar viejo. Cerrar la interconsulta es un UPDATE de `status`, y eso
-- basta para que el siguiente request del especialista sea denegado.
--
-- Por qué importa modelarlo así y no con una tabla de permisos: un permiso
-- persistido es un estado que hay que acordarse de revocar; derivarlo del estado
-- de la interconsulta hace que "olvidarse de revocar" no sea un caso posible.
--
-- `status`: OPEN | CLOSED. Constraint de base y no solo validación de aplicación —
-- es la columna de la que depende una garantía de seguridad (§8.1, revocación
-- temporal), y un valor inesperado acá haría que la comparación `= 'OPEN'` falle
-- de formas silenciosas.
CREATE TABLE interconsultation_requests (
    id                     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    patient_id             UUID        NOT NULL,
    clinical_encounter_id  UUID        NOT NULL,
    requesting_physician_id UUID       NOT NULL,
    specialist_user_id     UUID        NOT NULL,
    question               TEXT        NOT NULL,  -- cifrado
    status                 TEXT        NOT NULL DEFAULT 'OPEN',
    requested_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    closed_at              TIMESTAMPTZ,
    service_id             TEXT,                  -- AC-06b (del servicio que la solicita)

    CONSTRAINT interconsultation_status_valid CHECK (status IN ('OPEN', 'CLOSED'))
);

CREATE INDEX idx_interconsultations_specialist ON interconsultation_requests (specialist_user_id, patient_id, status);
CREATE INDEX idx_interconsultations_patient ON interconsultation_requests (patient_id);

-- InterconsultationResponse. Uno a uno con el request (§10: "has one"), en tabla
-- propia y no fusionado como el outcome de una intervención: acá no hay un índice
-- compuesto que obligue a lo contrario, y separar deja explícito que la respuesta
-- es un acto posterior de otra persona.
CREATE TABLE interconsultation_responses (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    interconsultation_id UUID        NOT NULL UNIQUE
        REFERENCES interconsultation_requests(id) ON DELETE RESTRICT,
    specialist_user_id   UUID        NOT NULL,
    opinion              TEXT        NOT NULL,  -- cifrado
    responded_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Prescription (FR-CLN-09). Se crea acá y no en Sub-fase 6 porque FR-CLN-09 la
-- necesita: una prescripción originada en una interconsulta debe vincularse al
-- encounter RAÍZ para trazabilidad completa. `clinical_encounter_id` es NOT NULL
-- justamente por eso — una prescripción sin encounter de origen no es trazable, que
-- es la propiedad que este requisito pide.
--
-- `interconsultation_id` nullable: una prescripción puede nacer de un encounter
-- normal, sin interconsulta de por medio.
CREATE TABLE prescriptions (
    id                     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    patient_id             UUID        NOT NULL,
    clinical_encounter_id  UUID        NOT NULL,
    interconsultation_id   UUID        REFERENCES interconsultation_requests(id) ON DELETE RESTRICT,
    prescriber_user_id     UUID        NOT NULL,
    medication             TEXT        NOT NULL,  -- cifrado
    dosage                 TEXT,                  -- cifrado
    instructions           TEXT,                  -- cifrado
    prescribed_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    service_id             TEXT,                  -- AC-06b

    CONSTRAINT prescriptions_encounter_required CHECK (clinical_encounter_id IS NOT NULL)
);

CREATE INDEX idx_prescriptions_patient ON prescriptions (patient_id);
CREATE INDEX idx_prescriptions_encounter ON prescriptions (clinical_encounter_id);

GRANT SELECT, INSERT, UPDATE ON interconsultation_requests TO {{app_role}};
GRANT SELECT, INSERT ON interconsultation_responses TO {{app_role}};
GRANT SELECT, INSERT ON prescriptions TO {{app_role}};

-- ============================================================================
-- Sub-fase 6 — Laboratorio (FR-CLN-11) y Farmacia (FR-CLN-12).
-- ============================================================================

-- LabOrder / LabResult. `critical_value` es un BOOLEAN explícito y no algo derivado
-- de comparar el resultado contra un rango: los rangos de referencia dependen del
-- método, el equipo y la población, y el laboratorio que emite el resultado es quien
-- sabe si ESE valor es crítico. Derivarlo acá sería inventar un criterio clínico.
CREATE TABLE lab_orders (
    id                     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    patient_id             UUID        NOT NULL,
    clinical_encounter_id  UUID        NOT NULL,
    ordering_physician_id  UUID        NOT NULL,
    test_code              TEXT        NOT NULL,
    test_name              TEXT        NOT NULL,
    ordered_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    service_id             TEXT,                  -- AC-06b
    -- Resultado, NULL hasta que el laboratorio lo carga. Mismo criterio de fusión que
    -- InterventionOutcome: uno a uno, siempre leídos juntos, y separar no aporta nada
    -- salvo un JOIN en cada lectura.
    result_value           TEXT,                  -- cifrado
    result_units           TEXT,
    critical_value         BOOLEAN,
    resulted_by_user_id    UUID,
    resulted_at            TIMESTAMPTZ
);

CREATE INDEX idx_lab_orders_patient ON lab_orders (patient_id);
CREATE INDEX idx_lab_orders_encounter ON lab_orders (clinical_encounter_id);
-- Índice parcial: la consulta que importa es "resultados críticos pendientes de que
-- el médico solicitante los vea", no "todos los resultados".
CREATE INDEX idx_lab_orders_critical ON lab_orders (ordering_physician_id, resulted_at)
    WHERE critical_value = TRUE;

-- Notificación in-app de valor crítico (FR-CLN-11). §16.4 deja fuera email/SMS en este
-- milestone, así que "notificar" acá es una fila que el médico solicitante consulta —
-- no un side effect que se pierde si nadie estaba mirando.
--
-- `acknowledged_at` NULL = todavía no la vio. Es lo que hace que la notificación sea
-- una obligación pendiente y no un mensaje que pasó y se fue.
CREATE TABLE critical_value_notifications (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    lab_order_id          UUID        NOT NULL REFERENCES lab_orders(id) ON DELETE RESTRICT,
    patient_id            UUID        NOT NULL,
    notify_user_id        UUID        NOT NULL,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    acknowledged_at       TIMESTAMPTZ,
    service_id            TEXT
);

CREATE INDEX idx_critical_notifications_pending ON critical_value_notifications (notify_user_id)
    WHERE acknowledged_at IS NULL;

-- DispensationRecord (FR-CLN-12). Lo registra el PHARMACIST contra una prescripción
-- existente. `doses_dispensed` alimenta el índice de adherencia.
CREATE TABLE dispensation_records (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    prescription_id     UUID        NOT NULL REFERENCES prescriptions(id) ON DELETE RESTRICT,
    patient_id          UUID        NOT NULL,
    pharmacist_user_id  UUID        NOT NULL,
    doses_dispensed     INTEGER     NOT NULL,
    dispensed_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    service_id          TEXT,

    CONSTRAINT dispensation_doses_positive CHECK (doses_dispensed > 0)
);

CREATE INDEX idx_dispensation_prescription ON dispensation_records (prescription_id);
CREATE INDEX idx_dispensation_patient ON dispensation_records (patient_id);

-- Columnas que FR-CLN-12 pide sobre la prescripción y Sub-fase 5 no necesitaba
-- (allí alcanzaba con medicación/dosis/instrucciones para demostrar FR-CLN-09).
-- `medication_class` en claro: es lo que permite detectar el conflicto de "misma clase
-- activa" en SQL sin descifrar cada prescripción del paciente. Es un dato de catálogo
-- farmacológico, no un identificador — mismo criterio que diagnosis_cie10.
ALTER TABLE prescriptions
    ADD COLUMN frequency        TEXT,   -- cifrado
    ADD COLUMN duration_days    INTEGER,
    ADD COLUMN route            TEXT,
    ADD COLUMN medication_class TEXT,
    ADD COLUMN total_doses      INTEGER;

CREATE INDEX idx_prescriptions_class ON prescriptions (patient_id, medication_class);

GRANT SELECT, INSERT, UPDATE ON lab_orders TO {{app_role}};
GRANT SELECT, INSERT, UPDATE ON critical_value_notifications TO {{app_role}};
GRANT SELECT, INSERT ON dispensation_records TO {{app_role}};
