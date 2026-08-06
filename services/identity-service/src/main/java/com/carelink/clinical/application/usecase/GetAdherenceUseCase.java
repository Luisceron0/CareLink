package com.carelink.clinical.application.usecase;

import com.carelink.clinical.domain.AdherenceIndex;
import com.carelink.clinical.domain.port.PharmacyRepository;
import com.carelink.clinical.domain.value.ServiceScope;
import com.carelink.identity.domain.value.TenantSlug;
import com.carelink.identity.infrastructure.audit.Auditable;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/** FR-CLN-12 — índice de adherencia. */
@Component
public class GetAdherenceUseCase {

    private final PharmacyRepository repository;

    public GetAdherenceUseCase(PharmacyRepository repository) {
        this.repository = repository;
    }

    @Auditable(action = "ADHERENCE_READ", tenantSlugExpression = "#tenantSlug.value()")
    public Optional<AdherenceIndex> execute(TenantSlug tenantSlug, UUID prescriptionId, ServiceScope scope) {
        return repository.adherenceFor(tenantSlug, prescriptionId, scope);
    }
}
