package com.carelink.clinical.infrastructure.persistence;

import com.carelink.clinical.domain.Interconsultation;
import com.carelink.clinical.domain.InterconsultationResponse;
import com.carelink.clinical.domain.port.EncryptionService;
import com.carelink.clinical.domain.port.InterconsultationRepository;
import com.carelink.clinical.domain.value.InterconsultationStatus;
import com.carelink.clinical.domain.value.ServiceScope;
import com.carelink.identity.domain.value.TenantSlug;
import com.carelink.identity.infrastructure.persistence.PostgresIdentifiers;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** FR-CLN-08, FR-CLN-10. Mismo criterio de cifrado y de sink que el resto del paquete. */
@Repository
public class JdbcInterconsultationRepository implements InterconsultationRepository {

    private final JdbcTemplate jdbcTemplate;
    private final EncryptionService encryptionService;

    public JdbcInterconsultationRepository(JdbcTemplate jdbcTemplate, EncryptionService encryptionService) {
        this.jdbcTemplate = jdbcTemplate;
        this.encryptionService = encryptionService;
    }

    @Override
    public void save(TenantSlug tenantSlug, Interconsultation ic) {
        String schema = schemaOf(tenantSlug);
        String slug = tenantSlug.value();
        jdbcTemplate.update(
                "INSERT INTO " + schema + ".interconsultation_requests " +
                        "(id, patient_id, clinical_encounter_id, requesting_physician_id, specialist_user_id, " +
                        "question, status, requested_at, service_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                ic.id(), ic.patientId(), ic.clinicalEncounterId(), ic.requestingPhysicianId(),
                ic.specialistUserId(), encryptionService.encrypt(ic.question(), slug),
                ic.status().name(), ic.requestedAt(), ic.serviceId());
    }

    /**
     * FR-CLN-10, AC-13 — la consulta que decide el acceso, evaluada en cada request.
     *
     * <p>Es un {@code EXISTS} contra el estado actual de la tabla: no hay caché, no hay
     * una fila de "permiso concedido" que pudiera haber quedado vieja, y no hay un
     * momento en el que este resultado se guarde para reusarlo después. Cerrar la
     * interconsulta (un {@code UPDATE status}) hace que la siguiente evaluación de esta
     * misma consulta devuelva false, sin ningún paso adicional de revocación — que es
     * exactamente lo que el requisito pide ("no persisted 'still has access' state to go
     * stale").
     */
    @Override
    public boolean specialistHasOpenAccess(TenantSlug tenantSlug, UUID specialistUserId, UUID patientId) {
        String schema = schemaOf(tenantSlug);
        Boolean exists = jdbcTemplate.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM " + schema + ".interconsultation_requests " +
                        "WHERE specialist_user_id = ? AND patient_id = ? AND status = 'OPEN')",
                Boolean.class, specialistUserId, patientId);
        return Boolean.TRUE.equals(exists);
    }

    @Override
    public Optional<Interconsultation> findById(TenantSlug tenantSlug, UUID id, ServiceScope scope) {
        String schema = schemaOf(tenantSlug);
        String slug = tenantSlug.value();

        String sql = "SELECT r.id, r.patient_id, r.clinical_encounter_id, r.requesting_physician_id, " +
                "r.specialist_user_id, r.question, r.status, r.requested_at, r.closed_at, r.service_id, " +
                "resp.id AS resp_id, resp.specialist_user_id AS resp_specialist, resp.opinion, resp.responded_at " +
                "FROM " + schema + ".interconsultation_requests r " +
                "LEFT JOIN " + schema + ".interconsultation_responses resp ON resp.interconsultation_id = r.id " +
                "WHERE r.id = ?";

        List<Interconsultation> results = scope.unrestricted()
                ? jdbcTemplate.query(sql, rowMapper(slug), id)
                : jdbcTemplate.query(sql + " AND r.service_id = ?", rowMapper(slug), id, scope.serviceId());
        return results.stream().findFirst();
    }

    @Override
    public boolean close(TenantSlug tenantSlug, UUID id, ServiceScope scope) {
        String schema = schemaOf(tenantSlug);
        // WHERE status = 'OPEN': cerrar dos veces no debe reescribir closed_at, porque
        // ese timestamp es el registro de cuándo cayó el acceso del especialista.
        String sql = "UPDATE " + schema + ".interconsultation_requests " +
                "SET status = 'CLOSED', closed_at = ? WHERE id = ? AND status = 'OPEN'";
        OffsetDateTime now = OffsetDateTime.now();
        int rows = scope.unrestricted()
                ? jdbcTemplate.update(sql, now, id)
                : jdbcTemplate.update(sql + " AND service_id = ?", now, id, scope.serviceId());
        return rows > 0;
    }

    @Override
    public boolean saveResponse(TenantSlug tenantSlug, InterconsultationResponse response) {
        String schema = schemaOf(tenantSlug);
        String slug = tenantSlug.value();

        // Solo se puede responder una interconsulta ABIERTA: responder una cerrada sería
        // escribir en una historia clínica a la que ya no se tiene acceso.
        Boolean open = jdbcTemplate.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM " + schema + ".interconsultation_requests " +
                        "WHERE id = ? AND specialist_user_id = ? AND status = 'OPEN')",
                Boolean.class, response.interconsultationId(), response.specialistUserId());
        if (!Boolean.TRUE.equals(open)) {
            return false;
        }

        jdbcTemplate.update(
                "INSERT INTO " + schema + ".interconsultation_responses " +
                        "(id, interconsultation_id, specialist_user_id, opinion, responded_at) VALUES (?, ?, ?, ?, ?)",
                response.id(), response.interconsultationId(), response.specialistUserId(),
                encryptionService.encrypt(response.opinion(), slug), response.respondedAt());
        return true;
    }

    private org.springframework.jdbc.core.RowMapper<Interconsultation> rowMapper(String slug) {
        return (rs, n) -> {
            UUID respId = rs.getObject("resp_id", UUID.class);
            InterconsultationResponse response = respId == null ? null : new InterconsultationResponse(
                    respId, rs.getObject("id", UUID.class), rs.getObject("resp_specialist", UUID.class),
                    encryptionService.decrypt(rs.getString("opinion"), slug),
                    rs.getObject("responded_at", OffsetDateTime.class));
            return new Interconsultation(
                    rs.getObject("id", UUID.class), rs.getObject("patient_id", UUID.class),
                    rs.getObject("clinical_encounter_id", UUID.class),
                    rs.getObject("requesting_physician_id", UUID.class),
                    rs.getObject("specialist_user_id", UUID.class),
                    encryptionService.decrypt(rs.getString("question"), slug),
                    InterconsultationStatus.valueOf(rs.getString("status")),
                    rs.getObject("requested_at", OffsetDateTime.class),
                    rs.getObject("closed_at", OffsetDateTime.class),
                    response, rs.getString("service_id"));
        };
    }

    /** Mismo patrón de revalidación en el sink que el resto de los repositorios (AC-05). */
    private String schemaOf(TenantSlug tenantSlug) {
        String slug = tenantSlug.value();
        if (!TenantSlug.PATTERN.matcher(slug).matches()) {
            throw new IllegalArgumentException("TenantSlug rechazado en el sink de InterconsultationRepository: " + slug);
        }
        return PostgresIdentifiers.quote("tenant_" + slug);
    }
}
