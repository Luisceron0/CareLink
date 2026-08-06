package com.carelink.clinical.application.usecase;

import com.carelink.clinical.domain.port.InterconsultationRepository;
import com.carelink.clinical.domain.value.ServiceScope;
import com.carelink.identity.domain.value.TenantSlug;
import com.carelink.identity.infrastructure.audit.Auditable;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * FR-CLN-10 — cerrar la interconsulta. No hay un paso de "revocar acceso" además de
 * este: el acceso del especialista se deriva del estado, así que cerrar ES revocar.
 */
@Component
public class CloseInterconsultationUseCase {

    private final InterconsultationRepository repository;

    public CloseInterconsultationUseCase(InterconsultationRepository repository) {
        this.repository = repository;
    }

    @Auditable(action = "INTERCONSULTATION_CLOSE", tenantSlugExpression = "#tenantSlug.value()")
    public boolean execute(TenantSlug tenantSlug, UUID interconsultationId, ServiceScope scope) {
        return repository.close(tenantSlug, interconsultationId, scope);
    }
}
