package com.carelink.clinical.application.usecase;

import com.carelink.clinical.domain.ClinicalEncounter;
import com.carelink.clinical.domain.port.ClinicalEncounterRepository;
import com.carelink.identity.domain.value.TenantSlug;
import com.carelink.identity.infrastructure.audit.Auditable;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
public class GetEncounterUseCase {
    private final ClinicalEncounterRepository repository;

    public GetEncounterUseCase(ClinicalEncounterRepository repository) {
        this.repository = repository;
    }

    @Auditable(action = "ENCOUNTER_READ", tenantSlugExpression = "#tenantSlug.value()")
    public Optional<ClinicalEncounter> execute(TenantSlug tenantSlug, UUID encounterId) {
        return repository.findById(tenantSlug, encounterId);
    }
}
