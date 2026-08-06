package com.carelink.clinical.application.usecase;

import com.carelink.clinical.domain.LabOrder;
import com.carelink.clinical.domain.port.LabRepository;
import com.carelink.identity.domain.value.TenantSlug;
import com.carelink.identity.infrastructure.audit.Auditable;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.UUID;

/** FR-CLN-11. */
@Component
public class OrderLabTestUseCase {

    private final LabRepository repository;

    public OrderLabTestUseCase(LabRepository repository) {
        this.repository = repository;
    }

    @Auditable(action = "LAB_ORDER_CREATE", tenantSlugExpression = "#tenantSlug.value()",
            patientIdExpression = "#patientId")
    public LabOrder execute(TenantSlug tenantSlug, UUID patientId, UUID encounterId, UUID orderingPhysicianId,
                             String testCode, String testName, String serviceId) {
        LabOrder order = new LabOrder(UUID.randomUUID(), patientId, encounterId, orderingPhysicianId,
                testCode, testName, OffsetDateTime.now(), null, null, null, null, null, serviceId);
        repository.saveOrder(tenantSlug, order);
        return order;
    }
}
