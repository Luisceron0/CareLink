package com.carelink.clinical.application.usecase;

import com.carelink.clinical.domain.PrescriptionConflict;
import com.carelink.clinical.domain.port.PharmacyRepository;
import com.carelink.clinical.domain.value.ServiceScope;
import com.carelink.identity.domain.value.TenantSlug;
import com.carelink.identity.infrastructure.audit.Auditable;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * FR-CLN-12 — "los avisos de conflicto ADVIERTEN, nunca bloquean". Este caso de uso
 * devuelve la lista; ningún camino de este código lanza una excepción por un conflicto
 * detectado, porque bloquear le quitaría al médico una decisión que puede tener razones
 * que el sistema no conoce.
 */
@Component
public class CheckPrescriptionConflictsUseCase {

    private final PharmacyRepository repository;

    public CheckPrescriptionConflictsUseCase(PharmacyRepository repository) {
        this.repository = repository;
    }

    @Auditable(action = "PRESCRIPTION_CONFLICT_CHECK", tenantSlugExpression = "#tenantSlug.value()",
            patientIdExpression = "#patientId")
    public List<PrescriptionConflict> execute(TenantSlug tenantSlug, UUID patientId, String medication,
                                               String medicationClass, ServiceScope scope) {
        return repository.detectConflicts(tenantSlug, patientId, medication, medicationClass, scope);
    }
}
