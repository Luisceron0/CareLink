package com.carelink.clinical.application.usecase;

import com.carelink.clinical.domain.ClinicalEncounter;
import com.carelink.clinical.domain.port.ClinicalEncounterRepository;
import com.carelink.identity.domain.value.TenantSlug;
import com.carelink.identity.infrastructure.audit.Auditable;
import org.springframework.stereotype.Component;

/**
 * AC-08: si {@code encounter} ya está firmado en la base, {@link
 * ClinicalEncounterRepository#update} lanza {@code EncounterAlreadySignedException} —
 * este caso de uso no la atrapa, la deja subir hasta el controller, que la traduce a 409.
 */
@Component
public class UpdateEncounterUseCase {
    private final ClinicalEncounterRepository repository;

    public UpdateEncounterUseCase(ClinicalEncounterRepository repository) {
        this.repository = repository;
    }

    @Auditable(action = "ENCOUNTER_UPDATE", tenantSlugExpression = "#tenantSlug.value()", patientIdExpression = "#encounter.patientId()")
    public void execute(TenantSlug tenantSlug, ClinicalEncounter encounter) {
        repository.update(tenantSlug, encounter);
    }
}
