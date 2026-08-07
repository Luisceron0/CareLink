package com.carelink.clinical.application.usecase;

import com.carelink.clinical.domain.DispensationRecord;
import com.carelink.clinical.domain.port.PharmacyRepository;
import com.carelink.clinical.domain.value.ServiceScope;
import com.carelink.identity.domain.value.TenantSlug;
import com.carelink.identity.infrastructure.audit.Auditable;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.UUID;

/** FR-CLN-12. */
@Component
public class DispenseMedicationUseCase {

    private final PharmacyRepository repository;

    public DispenseMedicationUseCase(PharmacyRepository repository) {
        this.repository = repository;
    }

    @Auditable(action = "MEDICATION_DISPENSE", tenantSlugExpression = "#tenantSlug.value()",
            patientIdExpression = "#patientId")
    public boolean execute(TenantSlug tenantSlug, UUID prescriptionId, UUID patientId, UUID pharmacistUserId,
                            int dosesDispensed, String serviceId, ServiceScope scope) {
        DispensationRecord record = new DispensationRecord(UUID.randomUUID(), prescriptionId, patientId,
                pharmacistUserId, dosesDispensed, OffsetDateTime.now(), serviceId);
        return repository.saveDispensation(tenantSlug, record, scope);
    }
}
