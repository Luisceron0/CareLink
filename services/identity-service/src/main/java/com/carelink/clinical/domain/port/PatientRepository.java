package com.carelink.clinical.domain.port;

import com.carelink.clinical.domain.Patient;
import com.carelink.identity.domain.value.TenantSlug;

import java.util.Optional;
import java.util.UUID;

/**
 * Patient vive en el schema del tenant (SRS §10), así que cada operación necesita
 * saber de qué tenant — no hay un "Patient global" que buscar por id solo.
 */
public interface PatientRepository {
    void save(TenantSlug tenantSlug, Patient patient);

    Optional<Patient> findById(TenantSlug tenantSlug, UUID patientId);
}
