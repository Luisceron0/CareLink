package com.carelink.clinical.infrastructure.persistence;

import com.carelink.clinical.domain.Prescription;
import com.carelink.clinical.domain.port.EncryptionService;
import com.carelink.clinical.domain.port.PrescriptionRepository;
import com.carelink.clinical.domain.value.ServiceScope;
import com.carelink.identity.domain.value.TenantSlug;
import com.carelink.identity.infrastructure.persistence.PostgresIdentifiers;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** FR-CLN-09. Medicación, dosis e instrucciones son texto libre — PHI, cifrado. */
@Repository
public class JdbcPrescriptionRepository implements PrescriptionRepository {

    private final JdbcTemplate jdbcTemplate;
    private final EncryptionService encryptionService;

    public JdbcPrescriptionRepository(JdbcTemplate jdbcTemplate, EncryptionService encryptionService) {
        this.jdbcTemplate = jdbcTemplate;
        this.encryptionService = encryptionService;
    }

    @Override
    public void save(TenantSlug tenantSlug, Prescription p) {
        String schema = schemaOf(tenantSlug);
        String slug = tenantSlug.value();
        jdbcTemplate.update(
                "INSERT INTO " + schema + ".prescriptions " +
                        "(id, patient_id, clinical_encounter_id, interconsultation_id, prescriber_user_id, " +
                        "medication, dosage, instructions, frequency, duration_days, route, medication_class, " +
                        "total_doses, prescribed_at, service_id) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                p.id(), p.patientId(), p.clinicalEncounterId(), p.interconsultationId(), p.prescriberUserId(),
                encryptionService.encrypt(p.medication(), slug), encryptNullable(p.dosage(), slug),
                encryptNullable(p.instructions(), slug), encryptNullable(p.frequency(), slug),
                p.durationDays(), p.route(), p.medicationClass(), p.totalDoses(),
                p.prescribedAt(), p.serviceId());
    }

    @Override
    public Optional<Prescription> findById(TenantSlug tenantSlug, UUID id, ServiceScope scope) {
        String schema = schemaOf(tenantSlug);
        String slug = tenantSlug.value();
        String sql = selectColumns() + " FROM " + schema + ".prescriptions WHERE id = ?";
        List<Prescription> rows = scope.unrestricted()
                ? jdbcTemplate.query(sql, rowMapper(slug), id)
                : jdbcTemplate.query(sql + " AND service_id = ?", rowMapper(slug), id, scope.serviceId());
        return rows.stream().findFirst();
    }

    @Override
    public List<Prescription> findByEncounter(TenantSlug tenantSlug, UUID encounterId, ServiceScope scope) {
        String schema = schemaOf(tenantSlug);
        String slug = tenantSlug.value();
        String sql = selectColumns() + " FROM " + schema + ".prescriptions WHERE clinical_encounter_id = ?";
        return scope.unrestricted()
                ? jdbcTemplate.query(sql + " ORDER BY prescribed_at", rowMapper(slug), encounterId)
                : jdbcTemplate.query(sql + " AND service_id = ? ORDER BY prescribed_at",
                        rowMapper(slug), encounterId, scope.serviceId());
    }

    private String selectColumns() {
        return "SELECT id, patient_id, clinical_encounter_id, interconsultation_id, prescriber_user_id, " +
                "medication, dosage, instructions, frequency, duration_days, route, medication_class, " +
                "total_doses, prescribed_at, service_id";
    }

    private RowMapper<Prescription> rowMapper(String slug) {
        return (rs, n) -> new Prescription(
                rs.getObject("id", UUID.class), rs.getObject("patient_id", UUID.class),
                rs.getObject("clinical_encounter_id", UUID.class),
                rs.getObject("interconsultation_id", UUID.class),
                rs.getObject("prescriber_user_id", UUID.class),
                encryptionService.decrypt(rs.getString("medication"), slug),
                decryptNullable(rs.getString("dosage"), slug),
                decryptNullable(rs.getString("instructions"), slug),
                decryptNullable(rs.getString("frequency"), slug),
                (Integer) rs.getObject("duration_days"), rs.getString("route"),
                rs.getString("medication_class"), (Integer) rs.getObject("total_doses"),
                rs.getObject("prescribed_at", OffsetDateTime.class), rs.getString("service_id"));
    }

    private String encryptNullable(String v, String slug) {
        return v == null ? null : encryptionService.encrypt(v, slug);
    }

    private String decryptNullable(String v, String slug) {
        return v == null ? null : encryptionService.decrypt(v, slug);
    }

    /** Mismo patrón de revalidación en el sink que el resto de los repositorios (AC-05). */
    private String schemaOf(TenantSlug tenantSlug) {
        String slug = tenantSlug.value();
        if (!TenantSlug.PATTERN.matcher(slug).matches()) {
            throw new IllegalArgumentException("TenantSlug rechazado en el sink de PrescriptionRepository: " + slug);
        }
        return PostgresIdentifiers.quote("tenant_" + slug);
    }
}
