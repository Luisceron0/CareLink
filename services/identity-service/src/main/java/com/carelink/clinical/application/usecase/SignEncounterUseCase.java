package com.carelink.clinical.application.usecase;

import com.carelink.clinical.domain.port.ClinicalEncounterRepository;
import com.carelink.identity.domain.value.TenantSlug;
import com.carelink.identity.infrastructure.audit.Auditable;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** FR-CLN-02 — "firmado vía acción autenticada". {@code signedByUserId} es quien firma,
 * no necesariamente quien creó el borrador (un médico puede firmar una nota que
 * empezó otro, según el flujo clínico real). */
@Component
public class SignEncounterUseCase {
    private final ClinicalEncounterRepository repository;

    public SignEncounterUseCase(ClinicalEncounterRepository repository) {
        this.repository = repository;
    }

    @Auditable(action = "ENCOUNTER_SIGN", tenantSlugExpression = "#tenantSlug.value()")
    public void execute(TenantSlug tenantSlug, UUID encounterId, UUID signedByUserId) {
        repository.sign(tenantSlug, encounterId, signedByUserId);
    }
}
