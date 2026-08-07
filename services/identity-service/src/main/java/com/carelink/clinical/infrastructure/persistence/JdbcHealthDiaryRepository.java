package com.carelink.clinical.infrastructure.persistence;

import com.carelink.clinical.domain.HealthDiaryEntry;
import com.carelink.clinical.domain.HealthIntervention;
import com.carelink.clinical.domain.InterventionOutcome;
import com.carelink.clinical.domain.VitalSigns;
import com.carelink.clinical.domain.port.EncryptionService;
import com.carelink.clinical.domain.port.HealthDiaryRepository;
import com.carelink.clinical.domain.value.ServiceScope;
import com.carelink.clinical.domain.value.Shift;
import com.carelink.identity.domain.value.TenantSlug;
import com.carelink.identity.infrastructure.persistence.PostgresIdentifiers;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * FR-CLN-04, FR-CLN-05. Mismo criterio de cifrado que el resto del paquete: texto libre
 * cifrado ({@code observations}, {@code description}, {@code outcome_notes}), códigos y
 * mediciones en claro.
 *
 * <p>{@code @Transactional} en {@link #save}: una entrada de diario con sus signos
 * vitales y sus intervenciones son tres INSERT que solo tienen sentido juntos. Sin
 * transacción, una falla a mitad de camino dejaría una entrada sin las intervenciones que
 * la justifican — una historia clínica incompleta que después se lee como completa.
 */
@Repository
public class JdbcHealthDiaryRepository implements HealthDiaryRepository {

    private final JdbcTemplate jdbcTemplate;
    private final EncryptionService encryptionService;

    public JdbcHealthDiaryRepository(JdbcTemplate jdbcTemplate, EncryptionService encryptionService) {
        this.jdbcTemplate = jdbcTemplate;
        this.encryptionService = encryptionService;
    }

    @Override
    @Transactional
    public void save(TenantSlug tenantSlug, HealthDiaryEntry entry) {
        String schema = schemaOf(tenantSlug);
        String slug = tenantSlug.value();

        jdbcTemplate.update(
                "INSERT INTO " + schema + ".health_diary_entries " +
                        "(id, patient_id, nurse_user_id, entry_date, shift, observations, service_id, created_at) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                entry.id(), entry.patientId(), entry.nurseUserId(),
                Date.valueOf(entry.entryDate()), entry.shift().name(),
                encryptNullable(entry.observations(), slug), entry.serviceId(), entry.createdAt());

        for (VitalSigns v : entry.vitalSigns()) {
            jdbcTemplate.update(
                    "INSERT INTO " + schema + ".vital_signs " +
                            "(id, diary_entry_id, systolic_mmhg, diastolic_mmhg, heart_rate_bpm, respiratory_rate, temperature_c, oxygen_saturation, recorded_at) " +
                            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    v.id(), entry.id(), v.systolicMmHg(), v.diastolicMmHg(), v.heartRateBpm(),
                    v.respiratoryRate(), v.temperatureCelsius(), v.oxygenSaturation(), v.recordedAt());
        }

        for (HealthIntervention i : entry.interventions()) {
            jdbcTemplate.update(
                    "INSERT INTO " + schema + ".health_interventions " +
                            "(id, diary_entry_id, patient_id, nanda_code, nic_code, diagnosis_cie10, description, performed_at, service_id) " +
                            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    i.id(), entry.id(), i.patientId(), i.nandaCode(), i.nicCode(), i.diagnosisCie10(),
                    encryptNullable(i.description(), slug), i.performedAt(), i.serviceId());
        }
    }

    @Override
    public Optional<HealthDiaryEntry> findById(TenantSlug tenantSlug, UUID entryId, ServiceScope scope) {
        String schema = schemaOf(tenantSlug);
        String slug = tenantSlug.value();

        // AC-06b: filtro en el WHERE, igual que el resto de los repositorios.
        String sql = "SELECT id, patient_id, nurse_user_id, entry_date, shift, observations, service_id, created_at " +
                "FROM " + schema + ".health_diary_entries WHERE id = ?";
        List<Object[]> head = scope.unrestricted()
                ? jdbcTemplate.query(sql, (rs, n) -> new Object[]{
                        rs.getObject("id", UUID.class), rs.getObject("patient_id", UUID.class),
                        rs.getObject("nurse_user_id", UUID.class), rs.getDate("entry_date").toLocalDate(),
                        rs.getString("shift"), rs.getString("observations"), rs.getString("service_id"),
                        rs.getObject("created_at", OffsetDateTime.class)}, entryId)
                : jdbcTemplate.query(sql + " AND service_id = ?", (rs, n) -> new Object[]{
                        rs.getObject("id", UUID.class), rs.getObject("patient_id", UUID.class),
                        rs.getObject("nurse_user_id", UUID.class), rs.getDate("entry_date").toLocalDate(),
                        rs.getString("shift"), rs.getString("observations"), rs.getString("service_id"),
                        rs.getObject("created_at", OffsetDateTime.class)}, entryId, scope.serviceId());

        if (head.isEmpty()) {
            return Optional.empty();
        }
        Object[] h = head.get(0);

        List<VitalSigns> vitals = jdbcTemplate.query(
                "SELECT id, diary_entry_id, systolic_mmhg, diastolic_mmhg, heart_rate_bpm, respiratory_rate, temperature_c, oxygen_saturation, recorded_at " +
                        "FROM " + schema + ".vital_signs WHERE diary_entry_id = ? ORDER BY recorded_at",
                (rs, n) -> new VitalSigns(
                        rs.getObject("id", UUID.class), rs.getObject("diary_entry_id", UUID.class),
                        (Integer) rs.getObject("systolic_mmhg"), (Integer) rs.getObject("diastolic_mmhg"),
                        (Integer) rs.getObject("heart_rate_bpm"), (Integer) rs.getObject("respiratory_rate"),
                        rs.getBigDecimal("temperature_c"), (Integer) rs.getObject("oxygen_saturation"),
                        rs.getObject("recorded_at", OffsetDateTime.class)),
                entryId);

        List<HealthIntervention> interventions = jdbcTemplate.query(
                "SELECT id, diary_entry_id, patient_id, nanda_code, nic_code, diagnosis_cie10, description, performed_at, " +
                        "noc_code, effectiveness, outcome_notes, outcome_recorded_at, service_id " +
                        "FROM " + schema + ".health_interventions WHERE diary_entry_id = ? ORDER BY performed_at",
                (rs, n) -> {
                    Integer effectiveness = (Integer) rs.getObject("effectiveness");
                    InterventionOutcome outcome = effectiveness == null ? null : new InterventionOutcome(
                            rs.getString("noc_code"), effectiveness,
                            decryptNullable(rs.getString("outcome_notes"), slug),
                            rs.getObject("outcome_recorded_at", OffsetDateTime.class));
                    return new HealthIntervention(
                            rs.getObject("id", UUID.class), rs.getObject("diary_entry_id", UUID.class),
                            rs.getObject("patient_id", UUID.class), rs.getString("nanda_code"),
                            rs.getString("nic_code"), rs.getString("diagnosis_cie10"),
                            decryptNullable(rs.getString("description"), slug),
                            rs.getObject("performed_at", OffsetDateTime.class), outcome, rs.getString("service_id"));
                },
                entryId);

        return Optional.of(new HealthDiaryEntry(
                (UUID) h[0], (UUID) h[1], (UUID) h[2], (java.time.LocalDate) h[3],
                Shift.valueOf((String) h[4]), decryptNullable((String) h[5], slug),
                vitals, interventions, (String) h[6], (OffsetDateTime) h[7]));
    }

    @Override
    public boolean recordOutcome(TenantSlug tenantSlug, UUID interventionId, InterventionOutcome outcome,
                                  ServiceScope scope) {
        String schema = schemaOf(tenantSlug);
        String slug = tenantSlug.value();

        // AC-06b sobre una mutación: el filtro va en el WHERE del UPDATE — sin ventana
        // entre comprobar y modificar, mismo criterio que linkClinicalEncounter.
        //
        // `AND effectiveness IS NULL`: registrar el resultado es una transición de una
        // sola dirección. Sin esa condición, un segundo POST sobreescribiría en silencio
        // la evaluación anterior, y esa evaluación ya alimentó agregados del Motor de
        // Conocimiento que se leen como evidencia clínica. 0 filas = "ya tenía outcome".
        String sql = "UPDATE " + schema + ".health_interventions " +
                "SET noc_code = ?, effectiveness = ?, outcome_notes = ?, outcome_recorded_at = ? " +
                "WHERE id = ? AND effectiveness IS NULL";
        Object[] base = {outcome.nocCode(), outcome.effectiveness(),
                encryptNullable(outcome.notes(), slug), outcome.recordedAt(), interventionId};

        int rows = scope.unrestricted()
                ? jdbcTemplate.update(sql, base)
                : jdbcTemplate.update(sql + " AND service_id = ?",
                        outcome.nocCode(), outcome.effectiveness(), encryptNullable(outcome.notes(), slug),
                        outcome.recordedAt(), interventionId, scope.serviceId());
        return rows > 0;
    }

    private String encryptNullable(String plaintext, String slug) {
        return plaintext == null ? null : encryptionService.encrypt(plaintext, slug);
    }

    private String decryptNullable(String stored, String slug) {
        return stored == null ? null : encryptionService.decrypt(stored, slug);
    }

    /** Mismo patrón de revalidación en el sink que el resto de los repositorios (AC-05). */
    private String schemaOf(TenantSlug tenantSlug) {
        String slug = tenantSlug.value();
        if (!TenantSlug.PATTERN.matcher(slug).matches()) {
            throw new IllegalArgumentException("TenantSlug rechazado en el sink de HealthDiaryRepository: " + slug);
        }
        return PostgresIdentifiers.quote("tenant_" + slug);
    }
}
