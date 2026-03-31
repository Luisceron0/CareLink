-- Tenant schema template: tables to be created per tenant schema
-- Apply this SQL into a schema named tenant_<tenant_slug> when provisioning

CREATE TABLE IF NOT EXISTS patients (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    full_name TEXT,
    phone TEXT,
    email TEXT,
    emergency_contact TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT now()
);

CREATE TABLE IF NOT EXISTS encounters (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    patient_id UUID NOT NULL,
    physician_id UUID NOT NULL,
    chief_complaint TEXT NOT NULL,
    physical_exam TEXT NOT NULL,
    treatment_plan TEXT NOT NULL,
    follow_up_instructions TEXT NOT NULL,
    signed_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT now()
);

CREATE TABLE IF NOT EXISTS physicians (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    full_name TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT now()
);

CREATE TABLE IF NOT EXISTS appointments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    physician_id UUID NOT NULL,
    patient_id UUID NOT NULL,
    slot_start TIMESTAMP WITH TIME ZONE NOT NULL,
    duration_minutes BIGINT NOT NULL DEFAULT 30,
    status TEXT NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_appointments_tenant_slot
    ON appointments(tenant_id, slot_start);

CREATE UNIQUE INDEX IF NOT EXISTS uq_appointments_physician_slot_active
    ON appointments(physician_id, slot_start)
    WHERE status IN ('PENDING', 'CONFIRMED', 'IN_PROGRESS');

CREATE TABLE IF NOT EXISTS phi_audit_log (
    id BIGSERIAL PRIMARY KEY,
    tenant_id UUID NOT NULL,
    actor_user_id UUID NOT NULL,
    target_patient_id UUID NOT NULL,
    action VARCHAR(80) NOT NULL,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_phi_audit_log_tenant_patient_time
    ON phi_audit_log(tenant_id, target_patient_id, occurred_at DESC);

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM pg_roles
        WHERE rolname = 'app_clinical_user'
    ) THEN
        EXECUTE 'REVOKE UPDATE, DELETE ON TABLE phi_audit_log '
            || 'FROM app_clinical_user';
        EXECUTE 'GRANT INSERT, SELECT ON TABLE phi_audit_log '
            || 'TO app_clinical_user';
    END IF;
END $$;
