package com.carelink.clinical.application.usecase;

import com.carelink.clinical.domain.Admission;
import com.carelink.clinical.domain.port.AdmissionRepository;
import com.carelink.identity.domain.value.TenantSlug;
import com.carelink.identity.infrastructure.audit.Auditable;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
public class GetAdmissionUseCase {

    private final AdmissionRepository admissionRepository;

    public GetAdmissionUseCase(AdmissionRepository admissionRepository) {
        this.admissionRepository = admissionRepository;
    }

    @Auditable(action = "ADMISSION_READ", tenantSlugExpression = "#tenantSlug.value()")
    public Optional<Admission> execute(TenantSlug tenantSlug, UUID admissionId) {
        return admissionRepository.findById(tenantSlug, admissionId);
    }
}
