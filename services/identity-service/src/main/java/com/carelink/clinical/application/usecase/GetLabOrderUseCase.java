package com.carelink.clinical.application.usecase;

import com.carelink.clinical.domain.LabOrder;
import com.carelink.clinical.domain.port.LabRepository;
import com.carelink.clinical.domain.value.ServiceScope;
import com.carelink.identity.domain.value.TenantSlug;
import com.carelink.identity.infrastructure.audit.Auditable;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
public class GetLabOrderUseCase {

    private final LabRepository repository;

    public GetLabOrderUseCase(LabRepository repository) {
        this.repository = repository;
    }

    @Auditable(action = "LAB_ORDER_READ", tenantSlugExpression = "#tenantSlug.value()")
    public Optional<LabOrder> execute(TenantSlug tenantSlug, UUID orderId, ServiceScope scope) {
        return repository.findOrderById(tenantSlug, orderId, scope);
    }
}
