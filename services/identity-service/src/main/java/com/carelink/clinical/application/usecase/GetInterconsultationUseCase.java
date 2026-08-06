package com.carelink.clinical.application.usecase;

import com.carelink.clinical.domain.Interconsultation;
import com.carelink.clinical.domain.port.InterconsultationRepository;
import com.carelink.clinical.domain.value.ServiceScope;
import com.carelink.identity.domain.value.TenantSlug;
import com.carelink.identity.infrastructure.audit.Auditable;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
public class GetInterconsultationUseCase {

    private final InterconsultationRepository repository;

    public GetInterconsultationUseCase(InterconsultationRepository repository) {
        this.repository = repository;
    }

    @Auditable(action = "INTERCONSULTATION_READ", tenantSlugExpression = "#tenantSlug.value()")
    public Optional<Interconsultation> execute(TenantSlug tenantSlug, UUID id, ServiceScope scope) {
        return repository.findById(tenantSlug, id, scope);
    }
}
