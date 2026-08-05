package com.carelink.clinical.application.usecase;

import com.carelink.clinical.domain.Admission;
import com.carelink.clinical.domain.port.AdmissionRepository;
import com.carelink.clinical.domain.value.AdmissionType;
import com.carelink.clinical.domain.value.TriagePriority;
import com.carelink.identity.domain.value.TenantSlug;
import com.carelink.identity.infrastructure.audit.Auditable;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * FR-CLN-03. {@code @Component}/{@code @Auditable} — mismo motivo que
 * {@code RegisterPatientUseCase}.
 *
 * <p>Triage Manchester es una herramienta de urgencias: {@code triagePriority} es
 * obligatorio para {@link AdmissionType#URGENCIAS} y se rechaza si viene presente para
 * {@link AdmissionType#CONSULTA_EXTERNA} — no es ambiguo dejar que cualquier admisión
 * lleve o no una prioridad a voluntad del caller, la regla vive acá, en un solo lugar.
 */
@Component
public class RegisterAdmissionUseCase {

    private final AdmissionRepository admissionRepository;

    public RegisterAdmissionUseCase(AdmissionRepository admissionRepository) {
        this.admissionRepository = admissionRepository;
    }

    @Auditable(action = "ADMISSION_REGISTER", tenantSlugExpression = "#tenantSlug.value()",
            patientIdExpression = "#patientId")
    public Admission execute(TenantSlug tenantSlug, UUID patientId, AdmissionType admissionType,
                              Integer triagePriorityValue, UUID admittedByUserId) {
        TriagePriority triagePriority = triagePriorityValue == null ? null : new TriagePriority(triagePriorityValue);

        if (admissionType == AdmissionType.URGENCIAS && triagePriority == null) {
            throw new IllegalArgumentException("Una admisión de URGENCIAS requiere prioridad de Triage Manchester (1-5)");
        }
        if (admissionType == AdmissionType.CONSULTA_EXTERNA && triagePriority != null) {
            throw new IllegalArgumentException("CONSULTA_EXTERNA no lleva clasificación de Triage Manchester");
        }

        Admission admission = new Admission(
                UUID.randomUUID(), patientId, admissionType, triagePriority,
                admittedByUserId, OffsetDateTime.now(), null, OffsetDateTime.now());
        admissionRepository.save(tenantSlug, admission);
        return admission;
    }
}
