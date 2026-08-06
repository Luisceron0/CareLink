package com.carelink.clinical.application.usecase;

import com.carelink.clinical.domain.ClinicalEncounter;
import com.carelink.clinical.domain.port.ClinicalEncounterRepository;
import com.carelink.identity.domain.value.TenantSlug;
import com.carelink.identity.infrastructure.audit.Auditable;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.UUID;

@Component
public class RegisterEncounterUseCase {
    private final ClinicalEncounterRepository repository;

    public RegisterEncounterUseCase(ClinicalEncounterRepository repository) {
        this.repository = repository;
    }

    @Auditable(action = "ENCOUNTER_CREATE", tenantSlugExpression = "#tenantSlug.value()", patientIdExpression = "#patientId")
    public ClinicalEncounter execute(TenantSlug tenantSlug, UUID patientId, UUID physicianUserId,
                                      String chiefComplaint, String examFindings, String diagnosisCie10,
                                      String treatmentPlan, String followUp, String serviceId) {
        ClinicalEncounter encounter = new ClinicalEncounter(
                UUID.randomUUID(), patientId, physicianUserId,
                chiefComplaint, examFindings, diagnosisCie10, treatmentPlan, followUp,
                serviceId, OffsetDateTime.now(), null, null);
        repository.save(tenantSlug, encounter);
        return encounter;
    }
}
