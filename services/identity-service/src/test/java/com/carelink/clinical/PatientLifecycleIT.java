package com.carelink.clinical;

import com.carelink.clinical.application.usecase.GetPatientUseCase;
import com.carelink.clinical.application.usecase.RegisterPatientUseCase;
import com.carelink.clinical.domain.Patient;
import com.carelink.clinical.domain.value.BloodType;
import com.carelink.clinical.domain.value.DocumentType;
import com.carelink.clinical.domain.value.ServiceScope;
import com.carelink.clinical.domain.value.Sex;
import com.carelink.identity.domain.port.SchemaProvisioner;
import com.carelink.identity.domain.value.TenantSlug;
import com.carelink.identity.support.EmbeddedPostgresSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * FR-CLN-01 de punta a punta, con los beans reales de Spring —no casos de uso armados a
 * mano— porque {@code @Auditable} solo se intercepta a través del proxy AOP que Spring
 * arma alrededor de sus propios beans. Esto es además la primera verificación real de
 * AC-07 ("1 lectura de PHI → 1 fila de audit_log"): hasta esta sub-fase era un test
 * placeholder porque no existía una entidad PHI real que leer.
 */
// classes = Application.class explícito: este test vive en com.carelink.clinical, que
// no es paquete padre ni hijo de com.carelink.identity (donde está @SpringBootApplication)
// — son hermanos bajo com.carelink — así que la búsqueda por convención de Spring Boot
// (hacia arriba desde el paquete del test) no lo encuentra sola.
@SpringBootTest(classes = com.carelink.identity.Application.class, properties = {
        "carelink.demo-mode=true",
        "carelink.app-env=test"
})
class PatientLifecycleIT {

    @Autowired
    private SchemaProvisioner schemaProvisioner;

    @Autowired
    private RegisterPatientUseCase registerPatientUseCase;

    @Autowired
    private GetPatientUseCase getPatientUseCase;

    @Autowired
    @Qualifier("adminJdbcTemplate")
    private JdbcTemplate adminJdbcTemplate;

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        EmbeddedPostgresSupport.registerDynamicProperties(registry, "patientlifecycle");
    }

    @Test
    @DisplayName("FR-CLN-01 + AC-07 — alta y lectura de un paciente, cifrado en reposo, auditado")
    void registerAndReadPatientRoundTripsAndAudits() {
        TenantSlug tenantSlug = new TenantSlug("patientclinic");
        schemaProvisioner.provisionSchema(tenantSlug);

        Patient created = registerPatientUseCase.execute(
                tenantSlug,
                "María Fernanda López",
                DocumentType.CEDULA_CIUDADANIA,
                "1020304050",
                LocalDate.of(1990, 5, 12),
                Sex.FEMALE,
                BloodType.O_POSITIVE,
                List.of("Penicilina", "Polen"),
                "Urgencias");

        // El dominio ve texto plano — el cifrado es un detalle del adaptador de
        // persistencia, no algo que el caso de uso o el test deban manejar.
        assertThat(created.fullName()).isEqualTo("María Fernanda López");

        Optional<Patient> read = getPatientUseCase.execute(tenantSlug, created.id(), ServiceScope.of("Urgencias"));
        assertThat(read).isPresent();
        Patient patient = read.get();
        assertThat(patient.fullName()).isEqualTo("María Fernanda López");
        assertThat(patient.documentId().number()).isEqualTo("1020304050");
        assertThat(patient.dateOfBirth()).isEqualTo(LocalDate.of(1990, 5, 12));
        assertThat(patient.sex()).isEqualTo(Sex.FEMALE);
        assertThat(patient.bloodType()).isEqualTo(BloodType.O_POSITIVE);
        assertThat(patient.allergies()).containsExactly("Penicilina", "Polen");

        // La fila en la tabla NO es texto plano — mismo tipo de verificación que
        // PhiColumnCannotBeReadAsPlaintextIT, pero acá contra el flujo real, no una
        // inserción manual.
        String storedFullName = adminJdbcTemplate.queryForObject(
                "SELECT full_name FROM tenant_patientclinic.patients WHERE id = ?",
                String.class, created.id());
        assertThat(storedFullName).doesNotContain("María").doesNotContain("López");

        // AC-07: la lectura de PHI que acaba de pasar generó su fila de auditoría.
        Integer readAuditRows = adminJdbcTemplate.queryForObject(
                "SELECT count(*) FROM tenant_patientclinic.audit_log WHERE action = 'PATIENT_READ' AND patient_id = ?",
                Integer.class, created.id());
        assertThat(readAuditRows).as("AC-07: 1 lectura de PHI -> 1 fila de audit_log").isEqualTo(1);

        Integer createAuditRows = adminJdbcTemplate.queryForObject(
                "SELECT count(*) FROM tenant_patientclinic.audit_log WHERE action = 'PATIENT_CREATE'",
                Integer.class);
        assertThat(createAuditRows).isEqualTo(1);
    }

    @Test
    @DisplayName("AC-06, a nivel de repositorio — pedir un paciente con el slug de otro tenant no devuelve nada")
    void patientFromOneTenantIsInvisibleUnderAnotherTenantSlug() {
        TenantSlug tenantA = new TenantSlug("crosstenanta");
        TenantSlug tenantB = new TenantSlug("crosstenantb");
        schemaProvisioner.provisionSchema(tenantA);
        schemaProvisioner.provisionSchema(tenantB);

        Patient patientInA = registerPatientUseCase.execute(
                tenantA, "Paciente Uno", DocumentType.CEDULA_CIUDADANIA, "1111111111",
                LocalDate.of(1985, 1, 1), Sex.MALE, BloodType.UNKNOWN, List.of(), "Urgencias");

        // No es que el chequeo "compare tenants y rechace" — la consulta con el slug de
        // B jamás toca el schema de A, así que el resultado es indistinguible de un id
        // que no existe en ningún lado.
        Optional<Patient> crossTenantRead = getPatientUseCase.execute(tenantB, patientInA.id(), ServiceScope.allServices());
        assertThat(crossTenantRead).isEmpty();

        Optional<Patient> sameTenantRead = getPatientUseCase.execute(tenantA, patientInA.id(), ServiceScope.allServices());
        assertThat(sameTenantRead).isPresent();
    }
}
