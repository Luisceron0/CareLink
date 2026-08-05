package com.carelink.clinical.infrastructure.persistence;

import com.carelink.clinical.domain.Admission;
import com.carelink.clinical.domain.port.AdmissionRepository;
import com.carelink.clinical.domain.value.AdmissionType;
import com.carelink.clinical.domain.value.TriagePriority;
import com.carelink.identity.domain.value.TenantSlug;
import com.carelink.identity.infrastructure.persistence.PostgresIdentifiers;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Sin cifrado — a diferencia de {@code JdbcPatientRepository}/
 * {@code JdbcClinicalEncounterRepository}, {@code admissions} no tiene ningún campo de
 * texto libre con PHI: tipo de admisión y prioridad de triage son categóricos (mismo
 * criterio que {@code role} en {@code users} o {@code diagnosis_cie10} en
 * {@code clinical_encounters}).
 */
@Repository
public class JdbcAdmissionRepository implements AdmissionRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcAdmissionRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void save(TenantSlug tenantSlug, Admission admission) {
        String schema = schemaOf(tenantSlug);
        jdbcTemplate.update(
                "INSERT INTO " + schema + ".admissions " +
                        "(id, patient_id, admission_type, triage_priority, admitted_by_user_id, admitted_at, clinical_encounter_id, created_at) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                admission.id(),
                admission.patientId(),
                admission.admissionType().name(),
                admission.triagePriority() == null ? null : admission.triagePriority().value(),
                admission.admittedByUserId(),
                admission.admittedAt(),
                admission.clinicalEncounterId(),
                admission.createdAt());
    }

    @Override
    public Optional<Admission> findById(TenantSlug tenantSlug, UUID admissionId) {
        String schema = schemaOf(tenantSlug);
        List<Admission> results = jdbcTemplate.query(
                "SELECT id, patient_id, admission_type, triage_priority, admitted_by_user_id, admitted_at, clinical_encounter_id, created_at " +
                        "FROM " + schema + ".admissions WHERE id = ?",
                rowMapper(),
                admissionId);
        return results.stream().findFirst();
    }

    @Override
    public boolean linkClinicalEncounter(TenantSlug tenantSlug, UUID admissionId, UUID clinicalEncounterId) {
        String schema = schemaOf(tenantSlug);
        int rows = jdbcTemplate.update(
                "UPDATE " + schema + ".admissions SET clinical_encounter_id = ? WHERE id = ?",
                clinicalEncounterId, admissionId);
        return rows > 0;
    }

    private RowMapper<Admission> rowMapper() {
        return (rs, rowNum) -> {
            Integer priority = (Integer) rs.getObject("triage_priority");
            return new Admission(
                    rs.getObject("id", UUID.class),
                    rs.getObject("patient_id", UUID.class),
                    AdmissionType.valueOf(rs.getString("admission_type")),
                    priority == null ? null : new TriagePriority(priority),
                    rs.getObject("admitted_by_user_id", UUID.class),
                    rs.getObject("admitted_at", OffsetDateTime.class),
                    rs.getObject("clinical_encounter_id", UUID.class),
                    rs.getObject("created_at", OffsetDateTime.class));
        };
    }

    /** Mismo patrón de revalidación en el sink que el resto de los repositorios de este paquete (AC-05). */
    private String schemaOf(TenantSlug tenantSlug) {
        String slug = tenantSlug.value();
        if (!TenantSlug.PATTERN.matcher(slug).matches()) {
            throw new IllegalArgumentException("TenantSlug rechazado en el sink de AdmissionRepository: " + slug);
        }
        return PostgresIdentifiers.quote("tenant_" + slug);
    }
}
