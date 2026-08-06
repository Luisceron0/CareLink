package com.carelink.clinical.infrastructure.persistence;

import com.carelink.clinical.domain.AdherenceIndex;
import com.carelink.clinical.domain.DispensationRecord;
import com.carelink.clinical.domain.PrescriptionConflict;
import com.carelink.clinical.domain.port.EncryptionService;
import com.carelink.clinical.domain.port.PharmacyRepository;
import com.carelink.clinical.domain.value.ServiceScope;
import com.carelink.identity.domain.value.TenantSlug;
import com.carelink.identity.infrastructure.persistence.PostgresIdentifiers;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/** FR-CLN-12. */
@Repository
public class JdbcPharmacyRepository implements PharmacyRepository {

    private final JdbcTemplate jdbcTemplate;
    private final EncryptionService encryptionService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public JdbcPharmacyRepository(JdbcTemplate jdbcTemplate, EncryptionService encryptionService) {
        this.jdbcTemplate = jdbcTemplate;
        this.encryptionService = encryptionService;
    }

    @Override
    public boolean saveDispensation(TenantSlug tenantSlug, DispensationRecord r, ServiceScope scope) {
        String schema = schemaOf(tenantSlug);

        // La prescripción tiene que existir y ser visible para este scope: dispensar
        // contra una prescripción de otro servicio es la misma clase de acceso cruzado
        // que AC-06b bloquea en las lecturas.
        String check = "SELECT EXISTS (SELECT 1 FROM " + schema + ".prescriptions WHERE id = ?";
        Boolean exists = scope.unrestricted()
                ? jdbcTemplate.queryForObject(check + ")", Boolean.class, r.prescriptionId())
                : jdbcTemplate.queryForObject(check + " AND service_id = ?)", Boolean.class,
                        r.prescriptionId(), scope.serviceId());
        if (!Boolean.TRUE.equals(exists)) {
            return false;
        }

        jdbcTemplate.update(
                "INSERT INTO " + schema + ".dispensation_records " +
                        "(id, prescription_id, patient_id, pharmacist_user_id, doses_dispensed, dispensed_at, service_id) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?)",
                r.id(), r.prescriptionId(), r.patientId(), r.pharmacistUserId(),
                r.dosesDispensed(), r.dispensedAt(), r.serviceId());
        return true;
    }

    @Override
    public Optional<AdherenceIndex> adherenceFor(TenantSlug tenantSlug, UUID prescriptionId, ServiceScope scope) {
        String schema = schemaOf(tenantSlug);

        String sql = "SELECT p.patient_id, p.total_doses, " +
                "COALESCE((SELECT SUM(d.doses_dispensed) FROM " + schema + ".dispensation_records d " +
                "          WHERE d.prescription_id = p.id), 0) AS dispensed " +
                "FROM " + schema + ".prescriptions p WHERE p.id = ?";

        List<AdherenceIndex> rows = scope.unrestricted()
                ? jdbcTemplate.query(sql, (rs, n) -> AdherenceIndex.of(
                        rs.getObject("patient_id", UUID.class), prescriptionId,
                        (Integer) rs.getObject("total_doses"), rs.getInt("dispensed")), prescriptionId)
                : jdbcTemplate.query(sql + " AND p.service_id = ?", (rs, n) -> AdherenceIndex.of(
                        rs.getObject("patient_id", UUID.class), prescriptionId,
                        (Integer) rs.getObject("total_doses"), rs.getInt("dispensed")),
                        prescriptionId, scope.serviceId());
        return rows.stream().findFirst();
    }

    /**
     * FR-CLN-12 — detecta conflictos, no los bloquea (esa decisión es del médico, ver
     * {@link PrescriptionConflict}).
     *
     * <p>Los dos conflictos se detectan de forma distinta por una razón concreta: las
     * alergias del paciente están CIFRADAS (AC-09), así que no se pueden comparar en SQL
     * y hay que descifrar la fila de ESE paciente y comparar en memoria — aceptable
     * porque es una sola fila, la del paciente que se está atendiendo, no un barrido.
     * La clase farmacológica está en claro justamente para que el segundo chequeo sí
     * pueda ser una consulta.
     *
     * <p>La coincidencia de alergia es por substring, insensible a mayúsculas, en ambas
     * direcciones. Es deliberadamente amplia: en una advertencia que no bloquea, un falso
     * positivo le cuesta al médico una lectura, y un falso negativo le cuesta al paciente
     * una reacción alérgica. No es un motor de interacciones farmacológicas — no existe
     * un catálogo de principios activos acá y no se inventa uno.
     */
    @Override
    public List<PrescriptionConflict> detectConflicts(TenantSlug tenantSlug, UUID patientId, String medication,
                                                       String medicationClass, ServiceScope scope) {
        String schema = schemaOf(tenantSlug);
        String slug = tenantSlug.value();
        List<PrescriptionConflict> conflicts = new ArrayList<>();

        if (medication != null && !medication.isBlank()) {
            List<String> allergiesJson = jdbcTemplate.query(
                    "SELECT allergies FROM " + schema + ".patients WHERE id = ?",
                    (rs, n) -> rs.getString("allergies"), patientId);
            for (String stored : allergiesJson) {
                if (stored == null) continue;
                for (String allergy : deserializeAllergies(encryptionService.decrypt(stored, slug))) {
                    if (matches(medication, allergy) || (medicationClass != null && matches(medicationClass, allergy))) {
                        conflicts.add(new PrescriptionConflict(PrescriptionConflict.Type.ALLERGY,
                                "El paciente tiene registrada alergia a '" + allergy + "'"));
                    }
                }
            }
        }

        if (medicationClass != null && !medicationClass.isBlank()) {
            String sql = "SELECT COUNT(*) FROM " + schema + ".prescriptions " +
                    "WHERE patient_id = ? AND medication_class = ?";
            Integer count = scope.unrestricted()
                    ? jdbcTemplate.queryForObject(sql, Integer.class, patientId, medicationClass)
                    : jdbcTemplate.queryForObject(sql + " AND service_id = ?", Integer.class,
                            patientId, medicationClass, scope.serviceId());
            if (count != null && count > 0) {
                conflicts.add(new PrescriptionConflict(PrescriptionConflict.Type.ACTIVE_SAME_CLASS,
                        "Ya hay " + count + " prescripción(es) activa(s) de la clase '" + medicationClass + "'"));
            }
        }

        return conflicts;
    }

    private boolean matches(String candidate, String allergy) {
        if (allergy == null || allergy.isBlank()) return false;
        String a = allergy.toLowerCase(Locale.ROOT).trim();
        String c = candidate.toLowerCase(Locale.ROOT).trim();
        return c.contains(a) || a.contains(c);
    }

    private List<String> deserializeAllergies(String json) {
        if (json == null) return List.of();
        try {
            return objectMapper.readValue(json, objectMapper.getTypeFactory()
                    .constructCollectionType(List.class, String.class));
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo deserializar allergies", e);
        }
    }

    /** Mismo patrón de revalidación en el sink que el resto de los repositorios (AC-05). */
    private String schemaOf(TenantSlug tenantSlug) {
        String slug = tenantSlug.value();
        if (!TenantSlug.PATTERN.matcher(slug).matches()) {
            throw new IllegalArgumentException("TenantSlug rechazado en el sink de PharmacyRepository: " + slug);
        }
        return PostgresIdentifiers.quote("tenant_" + slug);
    }
}
