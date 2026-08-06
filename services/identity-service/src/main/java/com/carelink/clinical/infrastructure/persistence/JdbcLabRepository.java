package com.carelink.clinical.infrastructure.persistence;

import com.carelink.clinical.domain.CriticalValueNotification;
import com.carelink.clinical.domain.LabOrder;
import com.carelink.clinical.domain.port.EncryptionService;
import com.carelink.clinical.domain.port.LabRepository;
import com.carelink.clinical.domain.value.ServiceScope;
import com.carelink.identity.domain.value.TenantSlug;
import com.carelink.identity.infrastructure.persistence.PostgresIdentifiers;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** FR-CLN-11. {@code result_value} es PHI de texto libre — cifrado; códigos y flags, no. */
@Repository
public class JdbcLabRepository implements LabRepository {

    private final JdbcTemplate jdbcTemplate;
    private final EncryptionService encryptionService;

    public JdbcLabRepository(JdbcTemplate jdbcTemplate, EncryptionService encryptionService) {
        this.jdbcTemplate = jdbcTemplate;
        this.encryptionService = encryptionService;
    }

    @Override
    public void saveOrder(TenantSlug tenantSlug, LabOrder o) {
        String schema = schemaOf(tenantSlug);
        jdbcTemplate.update(
                "INSERT INTO " + schema + ".lab_orders " +
                        "(id, patient_id, clinical_encounter_id, ordering_physician_id, test_code, test_name, ordered_at, service_id) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                o.id(), o.patientId(), o.clinicalEncounterId(), o.orderingPhysicianId(),
                o.testCode(), o.testName(), o.orderedAt(), o.serviceId());
    }

    @Override
    public Optional<LabOrder> findOrderById(TenantSlug tenantSlug, UUID orderId, ServiceScope scope) {
        String schema = schemaOf(tenantSlug);
        String slug = tenantSlug.value();
        String sql = "SELECT id, patient_id, clinical_encounter_id, ordering_physician_id, test_code, test_name, " +
                "ordered_at, result_value, result_units, critical_value, resulted_by_user_id, resulted_at, service_id " +
                "FROM " + schema + ".lab_orders WHERE id = ?";
        List<LabOrder> rows = scope.unrestricted()
                ? jdbcTemplate.query(sql, rowMapper(slug), orderId)
                : jdbcTemplate.query(sql + " AND service_id = ?", rowMapper(slug), orderId, scope.serviceId());
        return rows.stream().findFirst();
    }

    /**
     * FR-CLN-11. {@code @Transactional} porque cargar un resultado crítico son dos
     * escrituras que solo tienen sentido juntas: el resultado y la notificación al médico
     * solicitante. Un resultado crítico guardado sin su notificación es exactamente el
     * fallo que este requisito existe para prevenir — el valor está en el sistema y nadie
     * se entera.
     */
    @Override
    @Transactional
    public Optional<CriticalValueNotification> recordResult(TenantSlug tenantSlug, UUID orderId, String value,
                                                             String units, boolean critical, UUID resultedByUserId,
                                                             ServiceScope scope) {
        String schema = schemaOf(tenantSlug);
        String slug = tenantSlug.value();

        // WHERE resulted_at IS NULL: cargar el resultado es una transición de una sola
        // dirección, igual que firmar un encounter o registrar un outcome. Sobreescribir
        // un resultado de laboratorio ya emitido es corregir una historia clínica, y eso
        // no es un UPDATE silencioso.
        String sql = "UPDATE " + schema + ".lab_orders " +
                "SET result_value = ?, result_units = ?, critical_value = ?, resulted_by_user_id = ?, resulted_at = ? " +
                "WHERE id = ? AND resulted_at IS NULL";
        OffsetDateTime now = OffsetDateTime.now();
        String encrypted = value == null ? null : encryptionService.encrypt(value, slug);

        int rows = scope.unrestricted()
                ? jdbcTemplate.update(sql, encrypted, units, critical, resultedByUserId, now, orderId)
                : jdbcTemplate.update(sql + " AND service_id = ?", encrypted, units, critical,
                        resultedByUserId, now, orderId, scope.serviceId());
        if (rows == 0) {
            return Optional.empty();
        }

        if (!critical) {
            return Optional.empty();
        }

        // Los datos de la notificación salen de la orden, no de parámetros: a quién hay
        // que avisar lo define quién pidió el estudio, no quien carga el resultado.
        LabOrder order = findOrderById(tenantSlug, orderId, ServiceScope.allServices()).orElseThrow();
        CriticalValueNotification notification = new CriticalValueNotification(
                UUID.randomUUID(), orderId, order.patientId(), order.orderingPhysicianId(),
                now, null, order.serviceId());

        jdbcTemplate.update(
                "INSERT INTO " + schema + ".critical_value_notifications " +
                        "(id, lab_order_id, patient_id, notify_user_id, created_at, service_id) VALUES (?, ?, ?, ?, ?, ?)",
                notification.id(), notification.labOrderId(), notification.patientId(),
                notification.notifyUserId(), notification.createdAt(), notification.serviceId());

        return Optional.of(notification);
    }

    @Override
    public List<CriticalValueNotification> findPendingNotifications(TenantSlug tenantSlug, UUID userId) {
        String schema = schemaOf(tenantSlug);
        // Sin ServiceScope: estas notificaciones se dirigen a un usuario concreto
        // (notify_user_id), que es un filtro más estrecho que el servicio. Un médico ve
        // las suyas, no las de su departamento.
        return jdbcTemplate.query(
                "SELECT id, lab_order_id, patient_id, notify_user_id, created_at, acknowledged_at, service_id " +
                        "FROM " + schema + ".critical_value_notifications " +
                        "WHERE notify_user_id = ? AND acknowledged_at IS NULL ORDER BY created_at",
                (rs, n) -> new CriticalValueNotification(
                        rs.getObject("id", UUID.class), rs.getObject("lab_order_id", UUID.class),
                        rs.getObject("patient_id", UUID.class), rs.getObject("notify_user_id", UUID.class),
                        rs.getObject("created_at", OffsetDateTime.class),
                        rs.getObject("acknowledged_at", OffsetDateTime.class), rs.getString("service_id")),
                userId);
    }

    @Override
    public boolean acknowledgeNotification(TenantSlug tenantSlug, UUID notificationId, UUID userId) {
        String schema = schemaOf(tenantSlug);
        // AND notify_user_id = ?: solo el destinatario puede acusar recibo. Que otro lo
        // hiciera por él borraría el registro de que el médico responsable lo vio.
        int rows = jdbcTemplate.update(
                "UPDATE " + schema + ".critical_value_notifications SET acknowledged_at = ? " +
                        "WHERE id = ? AND notify_user_id = ? AND acknowledged_at IS NULL",
                OffsetDateTime.now(), notificationId, userId);
        return rows > 0;
    }

    private RowMapper<LabOrder> rowMapper(String slug) {
        return (rs, n) -> new LabOrder(
                rs.getObject("id", UUID.class), rs.getObject("patient_id", UUID.class),
                rs.getObject("clinical_encounter_id", UUID.class),
                rs.getObject("ordering_physician_id", UUID.class),
                rs.getString("test_code"), rs.getString("test_name"),
                rs.getObject("ordered_at", OffsetDateTime.class),
                rs.getString("result_value") == null ? null : encryptionService.decrypt(rs.getString("result_value"), slug),
                rs.getString("result_units"), (Boolean) rs.getObject("critical_value"),
                rs.getObject("resulted_by_user_id", UUID.class),
                rs.getObject("resulted_at", OffsetDateTime.class), rs.getString("service_id"));
    }

    /** Mismo patrón de revalidación en el sink que el resto de los repositorios (AC-05). */
    private String schemaOf(TenantSlug tenantSlug) {
        String slug = tenantSlug.value();
        if (!TenantSlug.PATTERN.matcher(slug).matches()) {
            throw new IllegalArgumentException("TenantSlug rechazado en el sink de LabRepository: " + slug);
        }
        return PostgresIdentifiers.quote("tenant_" + slug);
    }
}
