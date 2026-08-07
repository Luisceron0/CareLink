package com.carelink.clinical.application.usecase;

import com.carelink.clinical.domain.InterconsultationResponse;
import com.carelink.clinical.domain.port.InterconsultationRepository;
import com.carelink.identity.domain.value.TenantSlug;
import com.carelink.identity.infrastructure.audit.Auditable;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.UUID;

/** FR-CLN-08 — el especialista responde. Solo sobre una interconsulta abierta y dirigida a él. */
@Component
public class RespondInterconsultationUseCase {

    private final InterconsultationRepository repository;

    public RespondInterconsultationUseCase(InterconsultationRepository repository) {
        this.repository = repository;
    }

    @Auditable(action = "INTERCONSULTATION_RESPOND", tenantSlugExpression = "#tenantSlug.value()")
    public boolean execute(TenantSlug tenantSlug, UUID interconsultationId, UUID specialistUserId, String opinion) {
        InterconsultationResponse response = new InterconsultationResponse(
                UUID.randomUUID(), interconsultationId, specialistUserId, opinion, OffsetDateTime.now());
        return repository.saveResponse(tenantSlug, response);
    }
}
