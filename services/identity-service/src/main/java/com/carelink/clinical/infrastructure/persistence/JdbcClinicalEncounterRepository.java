package com.carelink.clinical.infrastructure.persistence;

import com.carelink.clinical.domain.ClinicalEncounter;
import com.carelink.clinical.domain.exception.EncounterAlreadySignedException;
import com.carelink.clinical.domain.port.ClinicalEncounterRepository;
import com.carelink.clinical.domain.port.EncryptionService;
import com.carelink.identity.domain.value.TenantSlug;
import com.carelink.identity.infrastructure.persistence.PostgresIdentifiers;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Mismo patrón que {@code JdbcPatientRepository}: JDBC directo con schema comillado,
 * cifra al escribir, descifra al leer.
 *
 * <p>Lo específico de esta clase es traducir el rechazo del trigger de inmutabilidad
 * (SQLSTATE {@code P0409}, definido en {@code tenant_template.sql}) a
 * {@link EncounterAlreadySignedException} — el resto del sistema (el controller) no
 * necesita saber que la garantía de AC-08 vive en un trigger de PostgreSQL.
 */
@Repository
public class JdbcClinicalEncounterRepository implements ClinicalEncounterRepository {

    private static final String SIGNED_IMMUTABLE_SQLSTATE = "P0409";

    private final JdbcTemplate jdbcTemplate;
    private final EncryptionService encryptionService;

    public JdbcClinicalEncounterRepository(JdbcTemplate jdbcTemplate, EncryptionService encryptionService) {
        this.jdbcTemplate = jdbcTemplate;
        this.encryptionService = encryptionService;
    }

    @Override
    public void save(TenantSlug tenantSlug, ClinicalEncounter encounter) {
        String schema = schemaOf(tenantSlug);
        String slug = tenantSlug.value();

        jdbcTemplate.update(
                "INSERT INTO " + schema + ".clinical_encounters " +
                        "(id, patient_id, physician_user_id, chief_complaint, exam_findings, diagnosis_cie10, treatment_plan, follow_up, created_at) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                encounter.id(),
                encounter.patientId(),
                encounter.physicianUserId(),
                encryptionService.encrypt(encounter.chiefComplaint(), slug),
                encryptNullable(encounter.examFindings(), slug),
                encounter.diagnosisCie10(),
                encryptNullable(encounter.treatmentPlan(), slug),
                encryptNullable(encounter.followUp(), slug),
                encounter.createdAt());
    }

    @Override
    public Optional<ClinicalEncounter> findById(TenantSlug tenantSlug, UUID encounterId) {
        String schema = schemaOf(tenantSlug);
        String slug = tenantSlug.value();

        List<ClinicalEncounter> results = jdbcTemplate.query(
                "SELECT id, patient_id, physician_user_id, chief_complaint, exam_findings, diagnosis_cie10, " +
                        "treatment_plan, follow_up, created_at, signed_at, signed_by_user_id " +
                        "FROM " + schema + ".clinical_encounters WHERE id = ?",
                rowMapper(slug),
                encounterId);
        return results.stream().findFirst();
    }

    @Override
    public void update(TenantSlug tenantSlug, ClinicalEncounter encounter) {
        String schema = schemaOf(tenantSlug);
        String slug = tenantSlug.value();

        try {
            int rows = jdbcTemplate.update(
                    "UPDATE " + schema + ".clinical_encounters SET " +
                            "chief_complaint = ?, exam_findings = ?, diagnosis_cie10 = ?, treatment_plan = ?, follow_up = ? " +
                            "WHERE id = ?",
                    encryptionService.encrypt(encounter.chiefComplaint(), slug),
                    encryptNullable(encounter.examFindings(), slug),
                    encounter.diagnosisCie10(),
                    encryptNullable(encounter.treatmentPlan(), slug),
                    encryptNullable(encounter.followUp(), slug),
                    encounter.id());
            if (rows == 0) {
                throw new IllegalArgumentException("ClinicalEncounter no encontrado: " + encounter.id());
            }
        } catch (DataAccessException e) {
            throw translateIfSignedImmutable(e, encounter.id());
        }
    }

    @Override
    public void sign(TenantSlug tenantSlug, UUID encounterId, UUID signedByUserId) {
        String schema = schemaOf(tenantSlug);

        // WHERE signed_at IS NULL: firmar es la única transición que el trigger permite
        // (OLD.signed_at IS NULL no dispara el bloqueo), pero re-firmar algo ya firmado
        // no debe afectar ninguna fila — 0 filas es la señal de "ya estaba firmado",
        // sin necesidad de que el trigger intervenga para este caso específico.
        int rows = jdbcTemplate.update(
                "UPDATE " + schema + ".clinical_encounters SET signed_at = ?, signed_by_user_id = ? " +
                        "WHERE id = ? AND signed_at IS NULL",
                OffsetDateTime.now(), signedByUserId, encounterId);
        if (rows == 0) {
            throw new EncounterAlreadySignedException(encounterId);
        }
    }

    /**
     * {@code java.sql.SQLException} de la API estándar, no {@code org.postgresql.util.PSQLException}
     * — el driver de Postgres tiene scope {@code runtime} en el pom (no hace falta en
     * compilación en ningún otro lado del código), y {@code getSQLState()} es parte de
     * la API JDBC estándar, no algo específico del driver.
     */
    private RuntimeException translateIfSignedImmutable(DataAccessException e, UUID encounterId) {
        Throwable cause = e;
        while (cause != null) {
            if (cause instanceof SQLException sqlEx
                    && SIGNED_IMMUTABLE_SQLSTATE.equals(sqlEx.getSQLState())) {
                return new EncounterAlreadySignedException(encounterId);
            }
            cause = cause.getCause();
        }
        return e;
    }

    private RowMapper<ClinicalEncounter> rowMapper(String tenantSlug) {
        return (rs, rowNum) -> new ClinicalEncounter(
                rs.getObject("id", UUID.class),
                rs.getObject("patient_id", UUID.class),
                rs.getObject("physician_user_id", UUID.class),
                encryptionService.decrypt(rs.getString("chief_complaint"), tenantSlug),
                decryptNullable(rs.getString("exam_findings"), tenantSlug),
                rs.getString("diagnosis_cie10"),
                decryptNullable(rs.getString("treatment_plan"), tenantSlug),
                decryptNullable(rs.getString("follow_up"), tenantSlug),
                rs.getObject("created_at", OffsetDateTime.class),
                rs.getObject("signed_at", OffsetDateTime.class),
                rs.getObject("signed_by_user_id", UUID.class));
    }

    private String encryptNullable(String plaintext, String tenantSlug) {
        return plaintext == null ? null : encryptionService.encrypt(plaintext, tenantSlug);
    }

    private String decryptNullable(String stored, String tenantSlug) {
        return stored == null ? null : encryptionService.decrypt(stored, tenantSlug);
    }

    /** Mismo patrón de revalidación en el sink que {@code JdbcPatientRepository} (AC-05). */
    private String schemaOf(TenantSlug tenantSlug) {
        String slug = tenantSlug.value();
        if (!TenantSlug.PATTERN.matcher(slug).matches()) {
            throw new IllegalArgumentException("TenantSlug rechazado en el sink de ClinicalEncounterRepository: " + slug);
        }
        return PostgresIdentifiers.quote("tenant_" + slug);
    }
}
