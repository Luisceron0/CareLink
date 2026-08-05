package com.carelink.clinical;

import com.carelink.clinical.application.usecase.GetAdmissionUseCase;
import com.carelink.clinical.application.usecase.GetEncounterUseCase;
import com.carelink.clinical.application.usecase.LinkEncounterToAdmissionUseCase;
import com.carelink.clinical.application.usecase.RegisterAdmissionUseCase;
import com.carelink.clinical.application.usecase.RegisterEncounterUseCase;
import com.carelink.clinical.domain.Admission;
import com.carelink.clinical.domain.ClinicalEncounter;
import com.carelink.clinical.domain.value.AdmissionType;
import com.carelink.identity.domain.port.SchemaProvisioner;
import com.carelink.identity.domain.value.TenantSlug;
import com.carelink.identity.support.EmbeddedPostgresSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * FR-CLN-03 de punta a punta, con los beans reales de Spring — mismo motivo que
 * {@code PatientLifecycleIT}: {@code @Auditable} solo se intercepta a través del proxy
 * AOP de beans que Spring administra.
 */
@SpringBootTest(classes = com.carelink.identity.Application.class, properties = {
        "carelink.demo-mode=true",
        "carelink.app-env=test"
})
class AdmissionLifecycleIT {

    @Autowired
    private SchemaProvisioner schemaProvisioner;

    @Autowired
    private RegisterAdmissionUseCase registerAdmissionUseCase;

    @Autowired
    private LinkEncounterToAdmissionUseCase linkEncounterToAdmissionUseCase;

    @Autowired
    private GetAdmissionUseCase getAdmissionUseCase;

    @Autowired
    private RegisterEncounterUseCase registerEncounterUseCase;

    @Autowired
    private GetEncounterUseCase getEncounterUseCase;

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        EmbeddedPostgresSupport.registerDynamicProperties(registry, "admissionlifecycle");
    }

    @Test
    @DisplayName("FR-CLN-03 — ingreso de urgencias con Triage Manchester, luego se abre y vincula un encounter")
    void admissionTriageAndEncounterLinkFlow() {
        TenantSlug tenantSlug = new TenantSlug("admissionclinic");
        schemaProvisioner.provisionSchema(tenantSlug);

        UUID patientId = UUID.randomUUID();
        UUID admissionsUserId = UUID.randomUUID();
        UUID physicianId = UUID.randomUUID();

        Admission admission = registerAdmissionUseCase.execute(
                tenantSlug, patientId, AdmissionType.URGENCIAS, 2, admissionsUserId);
        assertThat(admission.triagePriority().value()).isEqualTo(2);
        assertThat(admission.clinicalEncounterId()).isNull();

        ClinicalEncounter encounter = registerEncounterUseCase.execute(
                tenantSlug, patientId, physicianId,
                "Dolor torácico", "Auscultación sin hallazgos", "R07.4", "Reposo", "Control 48h");

        boolean linked = linkEncounterToAdmissionUseCase.execute(tenantSlug, admission.id(), encounter.id());
        assertThat(linked).isTrue();

        Optional<Admission> reread = getAdmissionUseCase.execute(tenantSlug, admission.id());
        assertThat(reread).isPresent();
        assertThat(reread.get().clinicalEncounterId()).isEqualTo(encounter.id());

        // Y el encounter en sí sigue siendo el mismo, legible por su propio caso de uso.
        assertThat(getEncounterUseCase.execute(tenantSlug, encounter.id())).isPresent();
    }

    @Test
    @DisplayName("URGENCIAS sin prioridad de triage se rechaza; CONSULTA_EXTERNA con prioridad también")
    void triagePriorityRequiredOnlyForUrgencias() {
        TenantSlug tenantSlug = new TenantSlug("admissionvalidation");
        schemaProvisioner.provisionSchema(tenantSlug);

        UUID patientId = UUID.randomUUID();
        UUID admissionsUserId = UUID.randomUUID();

        assertThatThrownBy(() -> registerAdmissionUseCase.execute(
                tenantSlug, patientId, AdmissionType.URGENCIAS, null, admissionsUserId))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> registerAdmissionUseCase.execute(
                tenantSlug, patientId, AdmissionType.CONSULTA_EXTERNA, 3, admissionsUserId))
                .isInstanceOf(IllegalArgumentException.class);

        // Contrapeso: CONSULTA_EXTERNA sin prioridad sí es válida.
        Admission admission = registerAdmissionUseCase.execute(
                tenantSlug, patientId, AdmissionType.CONSULTA_EXTERNA, null, admissionsUserId);
        assertThat(admission.triagePriority()).isNull();
    }

    @Test
    @DisplayName("AC-06 aplicado a admisiones — leer la admisión de otro tenant no devuelve nada")
    void admissionFromOneTenantIsInvisibleUnderAnotherTenantSlug() {
        TenantSlug tenantA = new TenantSlug("admissioncrossa");
        TenantSlug tenantB = new TenantSlug("admissioncrossb");
        schemaProvisioner.provisionSchema(tenantA);
        schemaProvisioner.provisionSchema(tenantB);

        Admission admission = registerAdmissionUseCase.execute(
                tenantA, UUID.randomUUID(), AdmissionType.CONSULTA_EXTERNA, null, UUID.randomUUID());

        assertThat(getAdmissionUseCase.execute(tenantB, admission.id())).isEmpty();
    }
}
