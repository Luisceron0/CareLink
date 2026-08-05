package com.carelink.clinical.application.usecase;

import com.carelink.clinical.domain.port.AdmissionRepository;
import com.carelink.identity.domain.value.TenantSlug;
import com.carelink.identity.infrastructure.audit.Auditable;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** FR-CLN-03 — "vincula al encounter cuando se abre uno". @Component/@Auditable, mismo motivo que el resto del paquete. */
@Component
public class LinkEncounterToAdmissionUseCase {

    private final AdmissionRepository admissionRepository;

    public LinkEncounterToAdmissionUseCase(AdmissionRepository admissionRepository) {
        this.admissionRepository = admissionRepository;
    }

    @Auditable(action = "ADMISSION_LINK_ENCOUNTER", tenantSlugExpression = "#tenantSlug.value()")
    public boolean execute(TenantSlug tenantSlug, UUID admissionId, UUID clinicalEncounterId) {
        return admissionRepository.linkClinicalEncounter(tenantSlug, admissionId, clinicalEncounterId);
    }
}
