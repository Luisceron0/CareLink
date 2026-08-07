package com.carelink.clinical.domain.port;

import com.carelink.clinical.domain.Admission;
import com.carelink.clinical.domain.value.ServiceScope;
import com.carelink.identity.domain.value.TenantSlug;

import java.util.Optional;
import java.util.UUID;

public interface AdmissionRepository {
    void save(TenantSlug tenantSlug, Admission admission);

    /** AC-06b: {@code scope} filtra por servicio. */
    Optional<Admission> findById(TenantSlug tenantSlug, UUID admissionId, ServiceScope scope);

    /** FR-CLN-03 — "vincula al encounter cuando se abre uno". 0 filas si el admissionId no existe en este tenant. */
    boolean linkClinicalEncounter(TenantSlug tenantSlug, UUID admissionId, UUID clinicalEncounterId, ServiceScope scope);
}
