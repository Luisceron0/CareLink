package com.carelink.clinical.application.usecase;

import com.carelink.clinical.domain.CriticalValueNotification;
import com.carelink.clinical.domain.port.LabRepository;
import com.carelink.clinical.domain.value.ServiceScope;
import com.carelink.identity.domain.value.TenantSlug;
import com.carelink.identity.infrastructure.audit.Auditable;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * FR-CLN-11 — cargar el resultado. Si viene marcado como crítico, la notificación al
 * médico solicitante se crea en la MISMA transacción que el resultado: un valor crítico
 * guardado sin su notificación es exactamente el fallo que el requisito previene.
 */
@Component
public class RecordLabResultUseCase {

    private final LabRepository repository;

    public RecordLabResultUseCase(LabRepository repository) {
        this.repository = repository;
    }

    @Auditable(action = "LAB_RESULT_RECORD", tenantSlugExpression = "#tenantSlug.value()")
    public Optional<CriticalValueNotification> execute(TenantSlug tenantSlug, UUID orderId, String value,
                                                        String units, boolean critical, UUID resultedByUserId,
                                                        ServiceScope scope) {
        return repository.recordResult(tenantSlug, orderId, value, units, critical, resultedByUserId, scope);
    }
}
