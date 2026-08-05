package com.carelink.clinical.infrastructure.persistence;

import com.carelink.clinical.domain.Patient;
import com.carelink.clinical.domain.port.EncryptionService;
import com.carelink.clinical.domain.port.PatientRepository;
import com.carelink.clinical.domain.value.BloodType;
import com.carelink.clinical.domain.value.DocumentId;
import com.carelink.clinical.domain.value.DocumentType;
import com.carelink.clinical.domain.value.Sex;
import com.carelink.identity.domain.value.TenantSlug;
import com.carelink.identity.infrastructure.persistence.PostgresIdentifiers;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Cifra antes de escribir, descifra al leer — el dominio ({@link Patient}) nunca ve un
 * valor cifrado, ni este adaptador expone uno hacia arriba. Usa el {@code JdbcTemplate}
 * primario (rol de aplicación, {@code carelink_app}): SELECT/INSERT sobre
 * {@code patients} es tráfico normal, no una operación de DDL como la que hace
 * {@link com.carelink.identity.infrastructure.provisioning.PostgresSchemaProvisioner}
 * con el rol administrador.
 *
 * <p>JDBC directo con el schema comillado ({@link PostgresIdentifiers#quote}), no JPA:
 * mismo patrón que {@code JdbcAuditEntryAdapter} — Patient vive en un schema dinámico
 * por tenant, y Hibernate no tiene una forma simple de apuntar una entidad a un schema
 * que solo se conoce en tiempo de ejecución sin adoptar multi-tenencia completa de
 * Hibernate, una pieza de infraestructura bastante más grande que lo que esta tarea
 * pide. Revisar esa decisión si el número de entidades clínicas crece lo suficiente
 * como para que escribir SQL a mano en cada repositorio deje de valer la pena.
 */
@Repository
public class JdbcPatientRepository implements PatientRepository {

    private final JdbcTemplate jdbcTemplate;
    private final EncryptionService encryptionService;
    private final ObjectMapper objectMapper;

    public JdbcPatientRepository(JdbcTemplate jdbcTemplate, EncryptionService encryptionService) {
        this.jdbcTemplate = jdbcTemplate;
        this.encryptionService = encryptionService;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public void save(TenantSlug tenantSlug, Patient patient) {
        String schema = schemaOf(tenantSlug);
        String slug = tenantSlug.value();

        jdbcTemplate.update(
                "INSERT INTO " + schema + ".patients " +
                        "(id, full_name, document_type, document_number, date_of_birth, sex, blood_type, allergies, created_at) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                patient.id(),
                encryptionService.encrypt(patient.fullName(), slug),
                patient.documentId().type().name(),
                encryptionService.encrypt(patient.documentId().number(), slug),
                encryptionService.encrypt(patient.dateOfBirth().toString(), slug),
                patient.sex().name(),
                patient.bloodType().name(),
                encryptionService.encrypt(serializeAllergies(patient.allergies()), slug),
                patient.createdAt());
    }

    @Override
    public Optional<Patient> findById(TenantSlug tenantSlug, UUID patientId) {
        String schema = schemaOf(tenantSlug);
        String slug = tenantSlug.value();

        List<Patient> results = jdbcTemplate.query(
                "SELECT id, full_name, document_type, document_number, date_of_birth, sex, blood_type, allergies, created_at " +
                        "FROM " + schema + ".patients WHERE id = ?",
                rowMapper(slug),
                patientId);
        return results.stream().findFirst();
    }

    private RowMapper<Patient> rowMapper(String tenantSlug) {
        return (rs, rowNum) -> new Patient(
                rs.getObject("id", UUID.class),
                encryptionService.decrypt(rs.getString("full_name"), tenantSlug),
                new DocumentId(
                        DocumentType.valueOf(rs.getString("document_type")),
                        encryptionService.decrypt(rs.getString("document_number"), tenantSlug)),
                LocalDate.parse(encryptionService.decrypt(rs.getString("date_of_birth"), tenantSlug)),
                Sex.valueOf(rs.getString("sex")),
                BloodType.valueOf(rs.getString("blood_type")),
                deserializeAllergies(decryptNullable(rs.getString("allergies"), tenantSlug)),
                rs.getObject("created_at", OffsetDateTime.class));
    }

    private String decryptNullable(String stored, String tenantSlug) {
        return stored == null ? null : encryptionService.decrypt(stored, tenantSlug);
    }

    private String serializeAllergies(List<String> allergies) {
        try {
            return objectMapper.writeValueAsString(allergies);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("No se pudo serializar allergies", e);
        }
    }

    private List<String> deserializeAllergies(String json) {
        if (json == null) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, objectMapper.getTypeFactory()
                    .constructCollectionType(List.class, String.class));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("No se pudo deserializar allergies", e);
        }
    }

    /**
     * Revalida en el sink contra {@code TenantSlug.PATTERN} y comilla el identificador
     * — mismo patrón que {@code PostgresSchemaProvisioner} y {@code JdbcAuditEntryAdapter}
     * (AC-05). Tres sitios independientes, un solo patrón de validación: es
     * intencional que cada uno revalide en vez de confiar en que el `TenantSlug` que
     * llegó ya pasó por el constructor en algún punto anterior (ADR-010).
     */
    private String schemaOf(TenantSlug tenantSlug) {
        String slug = tenantSlug.value();
        if (!TenantSlug.PATTERN.matcher(slug).matches()) {
            throw new IllegalArgumentException("TenantSlug rechazado en el sink de PatientRepository: " + slug);
        }
        return PostgresIdentifiers.quote("tenant_" + slug);
    }
}
