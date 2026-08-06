package com.carelink.clinical.application.usecase;

import com.carelink.clinical.domain.InterventionOutcome;
import com.carelink.clinical.domain.port.HealthDiaryRepository;
import com.carelink.clinical.domain.value.ServiceScope;
import com.carelink.identity.domain.value.TenantSlug;
import com.carelink.identity.infrastructure.audit.Auditable;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.UUID;

/** FR-CLN-05 — el resultado NOC + efectividad que alimenta el Motor de Conocimiento. */
@Component
public class RecordInterventionOutcomeUseCase {

    private final HealthDiaryRepository repository;

    public RecordInterventionOutcomeUseCase(HealthDiaryRepository repository) {
        this.repository = repository;
    }

    @Auditable(action = "INTERVENTION_OUTCOME_RECORD", tenantSlugExpression = "#tenantSlug.value()")
    public boolean execute(TenantSlug tenantSlug, UUID interventionId, String nocCode, int effectiveness,
                            String notes, ServiceScope scope) {
        InterventionOutcome outcome = new InterventionOutcome(nocCode, effectiveness, notes, OffsetDateTime.now());
        return repository.recordOutcome(tenantSlug, interventionId, outcome, scope);
    }
}
