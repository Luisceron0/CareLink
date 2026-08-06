package com.carelink.clinical;

import com.carelink.clinical.application.usecase.GetDiaryEntryUseCase;
import com.carelink.clinical.application.usecase.RecordDiaryEntryUseCase;
import com.carelink.clinical.application.usecase.RecordInterventionOutcomeUseCase;
import com.carelink.clinical.application.usecase.RegisterPatientUseCase;
import com.carelink.clinical.application.usecase.SearchKnowledgeUseCase;
import com.carelink.clinical.domain.HealthDiaryEntry;
import com.carelink.clinical.domain.HealthIntervention;
import com.carelink.clinical.domain.KnowledgeQuery;
import com.carelink.clinical.domain.KnowledgeResult;
import com.carelink.clinical.domain.Patient;
import com.carelink.clinical.domain.VitalSigns;
import com.carelink.clinical.domain.value.BloodType;
import com.carelink.clinical.domain.value.DocumentType;
import com.carelink.clinical.domain.value.ServiceScope;
import com.carelink.clinical.domain.value.Sex;
import com.carelink.clinical.domain.value.Shift;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * FR-CLN-04, FR-CLN-05, FR-CLN-06, FR-CLN-07 y AC-14, contra PostgreSQL real y con los
 * beans reales de Spring.
 */
@SpringBootTest(classes = com.carelink.identity.Application.class, properties = {
        "carelink.demo-mode=true",
        "carelink.app-env=test",
        // Umbral explícito en el test para no depender del default: si mañana alguien
        // cambia el default en application.yml, este test debe seguir probando k=5.
        "carelink.knowledge-anonymity-threshold=5"
})
class HealthDiaryAndKnowledgeEngineIT {

    private static final ServiceScope URGENCIAS = ServiceScope.of("Urgencias");

    @Autowired private SchemaProvisioner schemaProvisioner;
    @Autowired private RecordDiaryEntryUseCase recordDiaryEntryUseCase;
    @Autowired private GetDiaryEntryUseCase getDiaryEntryUseCase;
    @Autowired private RecordInterventionOutcomeUseCase recordInterventionOutcomeUseCase;
    @Autowired private RegisterPatientUseCase registerPatientUseCase;
    @Autowired private SearchKnowledgeUseCase searchKnowledgeUseCase;

    @Autowired @Qualifier("adminJdbcTemplate") private JdbcTemplate adminJdbcTemplate;

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        EmbeddedPostgresSupport.registerDynamicProperties(registry, "diaryknowledge");
    }

    @Test
    @DisplayName("FR-CLN-04/05 — entrada de diario con vitales e intervención, y luego su outcome NOC")
    void diaryEntryRoundTripAndOutcome() {
        TenantSlug tenant = new TenantSlug("diaryclinic");
        schemaProvisioner.provisionSchema(tenant);

        UUID patientId = UUID.randomUUID();
        UUID nurseId = UUID.randomUUID();

        HealthDiaryEntry created = recordDiaryEntryUseCase.execute(
                tenant, patientId, nurseId, LocalDate.of(2026, 8, 5), Shift.NOCHE,
                "Paciente refiere dolor moderado, tolera vía oral",
                List.of(new VitalSigns(null, null, 128, 82, 76, 18, new BigDecimal("37.2"), 97, null)),
                List.of(new HealthIntervention(null, null, patientId, "00132", "1400", "R10.4",
                        "Manejo del dolor: reposicionamiento y analgesia pautada", null, null, null)),
                "Urgencias");

        assertThat(created.vitalSigns()).hasSize(1);
        assertThat(created.interventions()).hasSize(1);
        assertThat(created.interventions().get(0).hasOutcome()).isFalse();

        Optional<HealthDiaryEntry> read = getDiaryEntryUseCase.execute(tenant, created.id(), URGENCIAS);
        assertThat(read).isPresent();
        assertThat(read.get().observations()).isEqualTo("Paciente refiere dolor moderado, tolera vía oral");
        assertThat(read.get().vitalSigns().get(0).heartRateBpm()).isEqualTo(76);
        assertThat(read.get().vitalSigns().get(0).temperatureCelsius()).isEqualByComparingTo("37.2");

        // Las observaciones se cifran en reposo; el código NIC no (lo agrupa el Motor).
        var row = adminJdbcTemplate.queryForMap(
                "SELECT observations FROM tenant_diaryclinic.health_diary_entries WHERE id = ?", created.id());
        assertThat((String) row.get("observations")).doesNotContain("dolor moderado");
        String nic = adminJdbcTemplate.queryForObject(
                "SELECT nic_code FROM tenant_diaryclinic.health_interventions WHERE diary_entry_id = ?",
                String.class, created.id());
        assertThat(nic).isEqualTo("1400");

        // FR-CLN-05: registrar el outcome.
        UUID interventionId = read.get().interventions().get(0).id();
        assertThat(recordInterventionOutcomeUseCase.execute(
                tenant, interventionId, "2102", 4, "Dolor descendió de 7/10 a 3/10", URGENCIAS)).isTrue();

        HealthDiaryEntry afterOutcome = getDiaryEntryUseCase.execute(tenant, created.id(), URGENCIAS).orElseThrow();
        assertThat(afterOutcome.interventions().get(0).hasOutcome()).isTrue();
        assertThat(afterOutcome.interventions().get(0).outcome().effectiveness()).isEqualTo(4);
        assertThat(afterOutcome.interventions().get(0).outcome().nocCode()).isEqualTo("2102");

        // Registrar un segundo outcome sobre la misma intervención NO sobreescribe: esa
        // evaluación ya alimentó agregados que se leen como evidencia clínica.
        assertThat(recordInterventionOutcomeUseCase.execute(
                tenant, interventionId, "9999", 1, "intento de sobreescritura", URGENCIAS)).isFalse();
        HealthDiaryEntry unchanged = getDiaryEntryUseCase.execute(tenant, created.id(), URGENCIAS).orElseThrow();
        assertThat(unchanged.interventions().get(0).outcome().effectiveness()).isEqualTo(4);
    }

    @Test
    @DisplayName("AC-14 — con 4 pacientes el resultado se SUPRIME; con el quinto aparece")
    void knowledgeEngineSuppressesBelowThresholdAndReturnsAtThreshold() {
        TenantSlug tenant = new TenantSlug("knowledgeclinic");
        schemaProvisioner.provisionSchema(tenant);

        // Cuatro pacientes distintos, misma combinación diagnóstico + intervención.
        for (int i = 0; i < 4; i++) {
            seedEvaluatedIntervention(tenant, "J45.9", "3140", "0410", 4);
        }

        KnowledgeQuery query = new KnowledgeQuery("J45.9", null, null, null, null);

        KnowledgeResult below = searchKnowledgeUseCase.execute(tenant, query);
        assertThat(below.suppressed())
                .as("AC-14: 4 < k=5 -> suprimido, y suprimido NO es lo mismo que vacío").isTrue();
        assertThat(below.rows()).isEmpty();

        // El quinto paciente cruza el umbral.
        seedEvaluatedIntervention(tenant, "J45.9", "3140", "0410", 5);

        KnowledgeResult atThreshold = searchKnowledgeUseCase.execute(tenant, query);
        assertThat(atThreshold.suppressed()).isFalse();
        assertThat(atThreshold.rows()).hasSize(1);
        assertThat(atThreshold.rows().get(0).nicCode()).isEqualTo("3140");
        assertThat(atThreshold.rows().get(0).distinctPatients()).isEqualTo(5);
        // 4 pacientes con efectividad 4 + 1 con 5 -> promedio 4.2
        assertThat(atThreshold.rows().get(0).averageEffectiveness()).isEqualTo(4.2);
    }

    @Test
    @DisplayName("AC-14 — 'suprimido' y 'no hay casos' son respuestas DISTINTAS, no las dos una lista vacía")
    void suppressedIsDistinguishableFromGenuinelyEmpty() {
        TenantSlug tenant = new TenantSlug("knowledgeempty");
        schemaProvisioner.provisionSchema(tenant);

        // Un solo paciente con este diagnóstico: hay datos, pero por debajo del umbral.
        seedEvaluatedIntervention(tenant, "E11.9", "2120", "2300", 3);

        KnowledgeResult conDatosOcultos = searchKnowledgeUseCase.execute(
                tenant, new KnowledgeQuery("E11.9", null, null, null, null));
        assertThat(conDatosOcultos.suppressed()).isTrue();
        assertThat(conDatosOcultos.rows()).isEmpty();

        // Un diagnóstico sobre el que no hay absolutamente nada: vacío legítimo.
        KnowledgeResult sinDatos = searchKnowledgeUseCase.execute(
                tenant, new KnowledgeQuery("Z99.9", null, null, null, null));
        assertThat(sinDatos.suppressed())
                .as("FR-CLN-07: 'no hay casos previos' no debe reportarse como supresión").isFalse();
        assertThat(sinDatos.rows()).isEmpty();
    }

    @Test
    @DisplayName("AC-14 — diez intervenciones sobre UN paciente siguen siendo un paciente: no cruzan el umbral")
    void manyInterventionsOnOnePatientDoNotDefeatKAnonymity() {
        TenantSlug tenant = new TenantSlug("knowledgeonepatient");
        schemaProvisioner.provisionSchema(tenant);

        UUID patientId = seedPatient(tenant);
        for (int i = 0; i < 10; i++) {
            seedEvaluatedInterventionForPatient(tenant, patientId, "C50.9", "6040", "1608", 5);
        }

        KnowledgeResult result = searchKnowledgeUseCase.execute(
                tenant, new KnowledgeQuery("C50.9", null, null, null, null));

        // Si el umbral contara intervenciones (COUNT(*)) en vez de pacientes distintos,
        // 10 >= 5 dejaría pasar los datos de UN paciente re-identificable — exactamente
        // el caso que ADR-007 quiere prevenir.
        assertThat(result.suppressed()).isTrue();
        assertThat(result.rows()).isEmpty();
    }

    @Test
    @DisplayName("una búsqueda sin ningún criterio clínico se rechaza — no es una búsqueda, es un volcado")
    void searchWithoutClinicalCriteriaIsRejected() {
        assertThatThrownBy(() -> new KnowledgeQuery(null, null, 20, 40, Sex.FEMALE))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("el filtro por edad falla explícitamente en vez de devolver un conjunto MÁS AMPLIO que el pedido")
    void ageFilterFailsLoudlyInsteadOfBeingIgnored() {
        TenantSlug tenant = new TenantSlug("knowledgeage");
        schemaProvisioner.provisionSchema(tenant);

        assertThatThrownBy(() -> searchKnowledgeUseCase.execute(
                tenant, new KnowledgeQuery("J45.9", null, 20, 40, null)))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("cifrada");
    }

    // ---------------------------------------------------------------- helpers

    private UUID seedPatient(TenantSlug tenant) {
        Patient p = registerPatientUseCase.execute(
                tenant, "Paciente " + UUID.randomUUID(), DocumentType.CEDULA_CIUDADANIA,
                String.valueOf(1000000000L + (long) (Math.random() * 899999999L)),
                LocalDate.of(1985, 3, 14), Sex.FEMALE, BloodType.O_POSITIVE, List.of(), "Urgencias");
        return p.id();
    }

    /** Un paciente nuevo con una intervención ya evaluada — la unidad que cuenta para el umbral. */
    private void seedEvaluatedIntervention(TenantSlug tenant, String cie10, String nic, String noc, int effectiveness) {
        seedEvaluatedInterventionForPatient(tenant, seedPatient(tenant), cie10, nic, noc, effectiveness);
    }

    private void seedEvaluatedInterventionForPatient(TenantSlug tenant, UUID patientId, String cie10,
                                                      String nic, String noc, int effectiveness) {
        HealthDiaryEntry entry = recordDiaryEntryUseCase.execute(
                tenant, patientId, UUID.randomUUID(), LocalDate.of(2026, 8, 5), Shift.MANANA, null,
                List.of(),
                List.of(new HealthIntervention(null, null, patientId, null, nic, cie10, null, null, null, null)),
                "Urgencias");
        recordInterventionOutcomeUseCase.execute(
                tenant, entry.interventions().get(0).id(), noc, effectiveness, null, URGENCIAS);
    }
}
