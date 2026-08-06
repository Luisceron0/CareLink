package com.carelink.clinical.application.usecase;

import com.carelink.clinical.domain.HealthDiaryEntry;
import com.carelink.clinical.domain.port.HealthDiaryRepository;
import com.carelink.clinical.domain.value.ServiceScope;
import com.carelink.identity.domain.value.TenantSlug;
import com.carelink.identity.infrastructure.audit.Auditable;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
public class GetDiaryEntryUseCase {

    private final HealthDiaryRepository repository;

    public GetDiaryEntryUseCase(HealthDiaryRepository repository) {
        this.repository = repository;
    }

    @Auditable(action = "DIARY_ENTRY_READ", tenantSlugExpression = "#tenantSlug.value()")
    public Optional<HealthDiaryEntry> execute(TenantSlug tenantSlug, UUID entryId, ServiceScope scope) {
        return repository.findById(tenantSlug, entryId, scope);
    }
}
