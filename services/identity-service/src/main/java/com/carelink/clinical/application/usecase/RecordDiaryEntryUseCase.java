package com.carelink.clinical.application.usecase;

import com.carelink.clinical.domain.HealthDiaryEntry;
import com.carelink.clinical.domain.HealthIntervention;
import com.carelink.clinical.domain.VitalSigns;
import com.carelink.clinical.domain.port.HealthDiaryRepository;
import com.carelink.clinical.domain.value.Shift;
import com.carelink.identity.domain.value.TenantSlug;
import com.carelink.identity.infrastructure.audit.Auditable;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** FR-CLN-04. {@code @Component}/{@code @Auditable} — mismo motivo que el resto del paquete. */
@Component
public class RecordDiaryEntryUseCase {

    private final HealthDiaryRepository repository;

    public RecordDiaryEntryUseCase(HealthDiaryRepository repository) {
        this.repository = repository;
    }

    @Auditable(action = "DIARY_ENTRY_CREATE", tenantSlugExpression = "#tenantSlug.value()",
            patientIdExpression = "#patientId")
    public HealthDiaryEntry execute(TenantSlug tenantSlug, UUID patientId, UUID nurseUserId,
                                     LocalDate entryDate, Shift shift, String observations,
                                     List<VitalSigns> vitalSigns, List<HealthIntervention> interventions,
                                     String serviceId) {
        UUID entryId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();

        // Los ids y el vínculo con la entrada se generan acá, no se aceptan del cliente:
        // dejar que el caller elija el diary_entry_id de una intervención permitiría
        // colgar intervenciones de la entrada de otro paciente.
        List<VitalSigns> vitals = vitalSigns == null ? List.of() : vitalSigns.stream()
                .map(v -> new VitalSigns(UUID.randomUUID(), entryId, v.systolicMmHg(), v.diastolicMmHg(),
                        v.heartRateBpm(), v.respiratoryRate(), v.temperatureCelsius(), v.oxygenSaturation(),
                        v.recordedAt() == null ? now : v.recordedAt()))
                .toList();

        List<HealthIntervention> withIds = interventions == null ? List.of() : interventions.stream()
                .map(i -> new HealthIntervention(UUID.randomUUID(), entryId, patientId, i.nandaCode(),
                        i.nicCode(), i.diagnosisCie10(), i.description(),
                        i.performedAt() == null ? now : i.performedAt(), null, serviceId))
                .toList();

        HealthDiaryEntry entry = new HealthDiaryEntry(entryId, patientId, nurseUserId, entryDate, shift,
                observations, vitals, withIds, serviceId, now);
        repository.save(tenantSlug, entry);
        return entry;
    }
}
