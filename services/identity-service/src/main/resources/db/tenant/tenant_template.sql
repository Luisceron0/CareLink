-- Tenant schema template: tables to be created per tenant schema
-- Apply this SQL into a schema named tenant_<tenant_slug> when provisioning

CREATE TABLE IF NOT EXISTS patients (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    full_name TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT now()
);

CREATE TABLE IF NOT EXISTS physicians (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    full_name TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT now()
);

CREATE TABLE IF NOT EXISTS appointments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    physician_id UUID,
    patient_id UUID,
    slot_start TIMESTAMP WITH TIME ZONE,
    status TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT now()
);
