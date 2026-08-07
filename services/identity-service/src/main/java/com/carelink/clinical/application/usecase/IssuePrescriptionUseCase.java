package com.carelink.clinical.application.usecase;

import com.carelink.clinical.domain.Prescription;
import com.carelink.clinical.domain.port.PrescriptionRepository;
import com.carelink.identity.domain.value.TenantSlug;
import com.carelink.identity.infrastructure.audit.Auditable;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * FR-CLN-09. {@code clinicalEncounterId} lo determina el llamador a partir de la
 * interconsulta —el encounter RAÍZ, no uno nuevo— para que la prescripción del
 * especialista quede colgando del mismo hilo de trazabilidad que originó la consulta.
 */
@Component
public class IssuePrescriptionUseCase {

    private final PrescriptionRepository repository;

    public IssuePrescriptionUseCase(PrescriptionRepository repository) {
        this.repository = repository;
    }

    @Auditable(action = "PRESCRIPTION_ISSUE", tenantSlugExpression = "#tenantSlug.value()",
            patientIdExpression = "#patientId")
    public Prescription execute(TenantSlug tenantSlug, UUID patientId, UUID clinicalEncounterId,
                                 UUID interconsultationId, UUID prescriberUserId, String medication,
                                 String dosage, String instructions, String frequency, Integer durationDays,
                                 String route, String medicationClass, Integer totalDoses, String serviceId) {
        Prescription p = new Prescription(
                UUID.randomUUID(), patientId, clinicalEncounterId, interconsultationId, prescriberUserId,
                medication, dosage, instructions, frequency, durationDays, route, medicationClass,
                totalDoses, OffsetDateTime.now(), serviceId);
        repository.save(tenantSlug, p);
        return p;
    }
}
