package com.carelink.clinical.domain.port;

import com.carelink.clinical.domain.AdherenceIndex;
import com.carelink.clinical.domain.DispensationRecord;
import com.carelink.clinical.domain.PrescriptionConflict;
import com.carelink.clinical.domain.value.ServiceScope;
import com.carelink.identity.domain.value.TenantSlug;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PharmacyRepository {

    /** FR-CLN-12. false si la prescripción no existe o no es del scope. */
    boolean saveDispensation(TenantSlug tenantSlug, DispensationRecord record, ServiceScope scope);

    /** FR-CLN-12 — índice de adherencia de una prescripción. Vacío si la prescripción no existe. */
    Optional<AdherenceIndex> adherenceFor(TenantSlug tenantSlug, UUID prescriptionId, ServiceScope scope);

    /**
     * FR-CLN-12 — conflictos detectados para una prescripción propuesta. ADVIERTE, no
     * bloquea: devolver una lista es parte de que quien decide sea el médico.
     */
    List<PrescriptionConflict> detectConflicts(TenantSlug tenantSlug, UUID patientId, String medication,
                                                String medicationClass, ServiceScope scope);
}
