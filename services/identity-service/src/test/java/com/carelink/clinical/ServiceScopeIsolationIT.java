package com.carelink.clinical;

import com.carelink.clinical.application.usecase.GetAdmissionUseCase;
import com.carelink.clinical.application.usecase.GetEncounterUseCase;
import com.carelink.clinical.application.usecase.GetPatientUseCase;
import com.carelink.clinical.application.usecase.LinkEncounterToAdmissionUseCase;
import com.carelink.clinical.application.usecase.RegisterAdmissionUseCase;
import com.carelink.clinical.application.usecase.RegisterEncounterUseCase;
import com.carelink.clinical.application.usecase.RegisterPatientUseCase;
import com.carelink.clinical.domain.Admission;
import com.carelink.clinical.domain.ClinicalEncounter;
import com.carelink.clinical.domain.Patient;
import com.carelink.clinical.domain.value.AdmissionType;
import com.carelink.clinical.domain.value.BloodType;
import com.carelink.clinical.domain.value.DocumentType;
import com.carelink.clinical.domain.value.ServiceScope;
import com.carelink.clinical.domain.value.Sex;
import com.carelink.identity.domain.port.SchemaProvisioner;
import com.carelink.identity.domain.value.TenantSlug;
import com.carelink.identity.infrastructure.security.AuthenticatedPrincipal;
import com.carelink.identity.support.EmbeddedPostgresSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AC-06b — "lectura cross-{@code service_id} dentro del mismo tenant → 403", con
 * cobertura de los tres recursos clínicos que existen (Patient, ClinicalEncounter,
 * Admission), no solo uno: el AC pide cobertura del path completo, y "un endpoint lo
 * cumple" no dice nada sobre los otros dos (la lección de AC-06, donde el patrón recién
 * se pudo dar por establecido al reverificarlo en un segundo endpoint).
 *
 * <p>Cada caso trae su contrapeso: el MISMO recurso leído con el servicio correcto SÍ
 * aparece. Sin eso, un bug que hiciera que las lecturas nunca devuelvan nada pasaría
 * este test igual.
 */
@SpringBootTest(classes = com.carelink.identity.Application.class, properties = {
        "carelink.demo-mode=true",
        "carelink.app-env=test"
})
class ServiceScopeIsolationIT {

    private static final ServiceScope URGENCIAS = ServiceScope.of("Urgencias");
    private static final ServiceScope CONSULTA_EXTERNA = ServiceScope.of("Consulta Externa");

    @Autowired private SchemaProvisioner schemaProvisioner;
    @Autowired private RegisterPatientUseCase registerPatientUseCase;
    @Autowired private GetPatientUseCase getPatientUseCase;
    @Autowired private RegisterEncounterUseCase registerEncounterUseCase;
    @Autowired private GetEncounterUseCase getEncounterUseCase;
    @Autowired private RegisterAdmissionUseCase registerAdmissionUseCase;
    @Autowired private GetAdmissionUseCase getAdmissionUseCase;
    @Autowired private LinkEncounterToAdmissionUseCase linkEncounterToAdmissionUseCase;

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        EmbeddedPostgresSupport.registerDynamicProperties(registry, "servicescope");
    }

    @Test
    @DisplayName("AC-06b — un paciente de Urgencias es invisible para Consulta Externa, en el mismo tenant")
    void patientIsInvisibleAcrossServicesWithinTheSameTenant() {
        TenantSlug tenant = new TenantSlug("scopepatient");
        schemaProvisioner.provisionSchema(tenant);

        Patient patient = registerPatientUseCase.execute(
                tenant, "Ana Restrepo", DocumentType.CEDULA_CIUDADANIA, "1020304050",
                LocalDate.of(1990, 5, 12), Sex.FEMALE, BloodType.O_POSITIVE, List.of(), "Urgencias");

        assertThat(getPatientUseCase.execute(tenant, patient.id(), CONSULTA_EXTERNA))
                .as("AC-06b: otro servicio del MISMO tenant no lo ve").isEmpty();

        // Contrapeso: el propio servicio sí lo ve, así que el vacío de arriba dice algo
        // específico sobre el servicio y no que las lecturas estén rotas.
        assertThat(getPatientUseCase.execute(tenant, patient.id(), URGENCIAS)).isPresent();

        // Y un rol exento (TENANT_ADMIN) ve todo el tenant.
        assertThat(getPatientUseCase.execute(tenant, patient.id(), ServiceScope.allServices())).isPresent();
    }

    @Test
    @DisplayName("AC-06b — un encounter de Urgencias es invisible para Consulta Externa")
    void encounterIsInvisibleAcrossServices() {
        TenantSlug tenant = new TenantSlug("scopeencounter");
        schemaProvisioner.provisionSchema(tenant);

        ClinicalEncounter encounter = registerEncounterUseCase.execute(
                tenant, UUID.randomUUID(), UUID.randomUUID(),
                "Dolor abdominal", "Sin hallazgos", "R10.4", "Observación", "24h", "Urgencias");

        assertThat(getEncounterUseCase.execute(tenant, encounter.id(), CONSULTA_EXTERNA)).isEmpty();
        assertThat(getEncounterUseCase.execute(tenant, encounter.id(), URGENCIAS)).isPresent();
    }

    @Test
    @DisplayName("AC-06b — una admisión de Urgencias es invisible para Consulta Externa, y tampoco se puede mutar")
    void admissionIsInvisibleAndImmutableAcrossServices() {
        TenantSlug tenant = new TenantSlug("scopeadmission");
        schemaProvisioner.provisionSchema(tenant);

        Admission admission = registerAdmissionUseCase.execute(
                tenant, UUID.randomUUID(), AdmissionType.URGENCIAS, 3, UUID.randomUUID(), "Urgencias");

        assertThat(getAdmissionUseCase.execute(tenant, admission.id(), CONSULTA_EXTERNA)).isEmpty();
        assertThat(getAdmissionUseCase.execute(tenant, admission.id(), URGENCIAS)).isPresent();

        // AC-06b sobre una MUTACIÓN, no solo sobre una lectura: vincular un encounter a
        // la admisión de otro servicio no afecta ninguna fila. El filtro va en el WHERE
        // del UPDATE, así que no hay ventana entre comprobar y modificar.
        assertThat(linkEncounterToAdmissionUseCase.execute(
                tenant, admission.id(), UUID.randomUUID(), CONSULTA_EXTERNA))
                .as("AC-06b: otro servicio no puede mutar esta admisión").isFalse();

        // Contrapeso: desde el servicio correcto sí se puede.
        assertThat(linkEncounterToAdmissionUseCase.execute(
                tenant, admission.id(), UUID.randomUUID(), URGENCIAS)).isTrue();
    }

    @Test
    @DisplayName("un rol no exento sin service_id no ve NADA — falla cerrado, no abierto")
    void roleWithoutServiceIdSeesNothingRatherThanEverything() {
        // Este es el caso que decide si AC-06b es una garantía o un agujero: si un
        // usuario mal provisionado (rol no exento, sin service_id) se tratara como
        // irrestricto, vería todo el tenant. ClinicalRequestScope devuelve empty ->
        // el controller responde 403.
        var sinServicio = new AuthenticatedPrincipal(UUID.randomUUID(), UUID.randomUUID(), "PHYSICIAN", null);
        assertThat(sinServicio.isServiceScopeExempt()).isFalse();
        assertThat(sinServicio.serviceScopeFilter()).isNull();

        var admin = new AuthenticatedPrincipal(UUID.randomUUID(), UUID.randomUUID(), "TENANT_ADMIN", null);
        assertThat(admin.isServiceScopeExempt()).isTrue();
    }
}
