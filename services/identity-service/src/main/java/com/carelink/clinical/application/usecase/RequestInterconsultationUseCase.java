package com.carelink.clinical.application.usecase;

import com.carelink.clinical.domain.Interconsultation;
import com.carelink.clinical.domain.port.InterconsultationRepository;
import com.carelink.clinical.domain.value.InterconsultationStatus;
import com.carelink.identity.domain.value.TenantSlug;
import com.carelink.identity.infrastructure.audit.Auditable;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.UUID;

/** FR-CLN-08 — un PHYSICIAN pide la opinión de un SPECIALIST sobre un paciente. */
@Component
public class RequestInterconsultationUseCase {

    private final InterconsultationRepository repository;

    public RequestInterconsultationUseCase(InterconsultationRepository repository) {
        this.repository = repository;
    }

    @Auditable(action = "INTERCONSULTATION_REQUEST", tenantSlugExpression = "#tenantSlug.value()",
            patientIdExpression = "#patientId")
    public Interconsultation execute(TenantSlug tenantSlug, UUID patientId, UUID clinicalEncounterId,
                                      UUID requestingPhysicianId, UUID specialistUserId, String question,
                                      String serviceId) {
        Interconsultation ic = new Interconsultation(
                UUID.randomUUID(), patientId, clinicalEncounterId, requestingPhysicianId, specialistUserId,
                question, InterconsultationStatus.OPEN, OffsetDateTime.now(), null, null, serviceId);
        repository.save(tenantSlug, ic);
        return ic;
    }
}
