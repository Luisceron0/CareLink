package com.carelink.clinical;

import com.carelink.clinical.application.usecase.CloseInterconsultationUseCase;
import com.carelink.clinical.application.usecase.GetInterconsultationUseCase;
import com.carelink.clinical.application.usecase.IssuePrescriptionUseCase;
import com.carelink.clinical.application.usecase.RequestInterconsultationUseCase;
import com.carelink.clinical.application.usecase.RespondInterconsultationUseCase;
import com.carelink.clinical.domain.Interconsultation;
import com.carelink.clinical.domain.Prescription;
import com.carelink.clinical.domain.port.InterconsultationRepository;
import com.carelink.clinical.domain.port.PrescriptionRepository;
import com.carelink.clinical.domain.value.InterconsultationStatus;
import com.carelink.clinical.domain.value.ServiceScope;
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

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** FR-CLN-08, FR-CLN-09, FR-CLN-10 y AC-13, contra PostgreSQL real. */
@SpringBootTest(classes = com.carelink.identity.Application.class, properties = {
        "carelink.demo-mode=true",
        "carelink.app-env=test"
})
class InterconsultationLifecycleIT {

    private static final ServiceScope URGENCIAS = ServiceScope.of("Urgencias");

    @Autowired private SchemaProvisioner schemaProvisioner;
    @Autowired private RequestInterconsultationUseCase requestUseCase;
    @Autowired private RespondInterconsultationUseCase respondUseCase;
    @Autowired private CloseInterconsultationUseCase closeUseCase;
    @Autowired private GetInterconsultationUseCase getUseCase;
    @Autowired private IssuePrescriptionUseCase issuePrescriptionUseCase;
    @Autowired private InterconsultationRepository interconsultationRepository;
    @Autowired private PrescriptionRepository prescriptionRepository;
    @Autowired @Qualifier("adminJdbcTemplate") private JdbcTemplate adminJdbcTemplate;

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        EmbeddedPostgresSupport.registerDynamicProperties(registry, "interconsult");
    }

    @Test
    @DisplayName("AC-13 — grant → acceso concedido → close → el siguiente request del especialista es denegado")
    void accessIsRevokedTheInstantTheInterconsultationCloses() {
        TenantSlug tenant = new TenantSlug("ac13clinic");
        schemaProvisioner.provisionSchema(tenant);

        UUID patientId = UUID.randomUUID();
        UUID physicianId = UUID.randomUUID();
        UUID specialistId = UUID.randomUUID();

        // Antes de que exista la interconsulta, el especialista no tiene acceso —
        // contrapeso imprescindible: sin él, un método que devolviera siempre false
        // pasaría la parte de "revocado" de este test sin probar nada.
        assertThat(interconsultationRepository.specialistHasOpenAccess(tenant, specialistId, patientId))
                .as("sin interconsulta no hay acceso").isFalse();

        Interconsultation ic = requestUseCase.execute(
                tenant, patientId, UUID.randomUUID(), physicianId, specialistId,
                "¿Amerita broncodilatador de rescate?", "Urgencias");

        assertThat(interconsultationRepository.specialistHasOpenAccess(tenant, specialistId, patientId))
                .as("con la interconsulta abierta, sí").isTrue();

        // Cerrar. No hay ningún paso de "revocar acceso" además de esto.
        assertThat(closeUseCase.execute(tenant, ic.id(), URGENCIAS)).isTrue();

        // AC-13: la MISMA pregunta, sin nada más de por medio, ahora dice que no.
        assertThat(interconsultationRepository.specialistHasOpenAccess(tenant, specialistId, patientId))
                .as("AC-13: cerrada la interconsulta, el siguiente request es denegado").isFalse();

        // Y no queda ningún estado persistido de "todavía tiene acceso" que pudiera
        // quedar viejo: lo único que cambió es el status de la interconsulta.
        String status = adminJdbcTemplate.queryForObject(
                "SELECT status FROM tenant_ac13clinic.interconsultation_requests WHERE id = ?",
                String.class, ic.id());
        assertThat(status).isEqualTo("CLOSED");
    }

    @Test
    @DisplayName("FR-CLN-10 — el acceso es por PACIENTE, no un permiso general del especialista")
    void accessIsScopedToThePatientOfTheInterconsultation() {
        TenantSlug tenant = new TenantSlug("ac13scope");
        schemaProvisioner.provisionSchema(tenant);

        UUID pacienteConConsulta = UUID.randomUUID();
        UUID otroPaciente = UUID.randomUUID();
        UUID specialistId = UUID.randomUUID();

        requestUseCase.execute(tenant, pacienteConConsulta, UUID.randomUUID(), UUID.randomUUID(),
                specialistId, "Interconsulta de este paciente", "Urgencias");

        assertThat(interconsultationRepository.specialistHasOpenAccess(tenant, specialistId, pacienteConConsulta))
                .isTrue();
        assertThat(interconsultationRepository.specialistHasOpenAccess(tenant, specialistId, otroPaciente))
                .as("una interconsulta abierta no da acceso a los demás pacientes").isFalse();
    }

    @Test
    @DisplayName("FR-CLN-08 — el especialista responde mientras está abierta; después de cerrar, no")
    void specialistCanOnlyRespondWhileOpen() {
        TenantSlug tenant = new TenantSlug("respondclinic");
        schemaProvisioner.provisionSchema(tenant);

        UUID specialistId = UUID.randomUUID();
        Interconsultation ic = requestUseCase.execute(
                tenant, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), specialistId,
                "¿Conducta sugerida?", "Urgencias");

        assertThat(respondUseCase.execute(tenant, ic.id(), specialistId, "Sugiero manejo conservador")).isTrue();

        Interconsultation conRespuesta = getUseCase.execute(tenant, ic.id(), URGENCIAS).orElseThrow();
        assertThat(conRespuesta.response()).isNotNull();
        assertThat(conRespuesta.response().opinion()).isEqualTo("Sugiero manejo conservador");

        // Otro especialista no puede responder una interconsulta que no es suya.
        assertThat(respondUseCase.execute(tenant, ic.id(), UUID.randomUUID(), "opinión de un tercero")).isFalse();

        closeUseCase.execute(tenant, ic.id(), URGENCIAS);
        assertThat(respondUseCase.execute(tenant, ic.id(), specialistId, "tarde"))
                .as("responder una interconsulta cerrada es escribir sin acceso").isFalse();
    }

    @Test
    @DisplayName("FR-CLN-09 — la prescripción del especialista se vincula al encounter RAÍZ, no a uno nuevo")
    void prescriptionFromInterconsultationLinksToRootEncounter() {
        TenantSlug tenant = new TenantSlug("prescriptionclinic");
        schemaProvisioner.provisionSchema(tenant);

        UUID patientId = UUID.randomUUID();
        UUID rootEncounterId = UUID.randomUUID();
        UUID specialistId = UUID.randomUUID();

        Interconsultation ic = requestUseCase.execute(
                tenant, patientId, rootEncounterId, UUID.randomUUID(), specialistId,
                "¿Requiere anticoagulación?", "Urgencias");

        Prescription p = issuePrescriptionUseCase.execute(
                tenant, ic.patientId(), ic.clinicalEncounterId(), ic.id(), specialistId,
                "Enoxaparina", "40 mg SC cada 24h", "Control anti-Xa a las 72h", "Urgencias");

        assertThat(p.clinicalEncounterId())
                .as("FR-CLN-09: trazabilidad hasta el encounter raíz").isEqualTo(rootEncounterId);
        assertThat(p.originatedInInterconsultation()).isTrue();

        // Y aparece al listar las prescripciones del encounter raíz — que es lo que
        // "trazabilidad completa" significa en la práctica.
        List<Prescription> delEncounter = prescriptionRepository.findByEncounter(tenant, rootEncounterId, URGENCIAS);
        assertThat(delEncounter).hasSize(1);
        assertThat(delEncounter.get(0).medication()).isEqualTo("Enoxaparina");

        // La medicación se cifra en reposo.
        String stored = adminJdbcTemplate.queryForObject(
                "SELECT medication FROM tenant_prescriptionclinic.prescriptions WHERE id = ?",
                String.class, p.id());
        assertThat(stored).doesNotContain("Enoxaparina");
    }

    @Test
    @DisplayName("cerrar dos veces no reescribe closed_at — es el registro de cuándo cayó el acceso")
    void closingTwiceDoesNotRewriteTheTimestamp() {
        TenantSlug tenant = new TenantSlug("doubleclose");
        schemaProvisioner.provisionSchema(tenant);

        Interconsultation ic = requestUseCase.execute(
                tenant, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "pregunta", "Urgencias");

        assertThat(closeUseCase.execute(tenant, ic.id(), URGENCIAS)).isTrue();
        var primerCierre = getUseCase.execute(tenant, ic.id(), URGENCIAS).orElseThrow().closedAt();

        assertThat(closeUseCase.execute(tenant, ic.id(), URGENCIAS)).isFalse();
        var segundoIntento = getUseCase.execute(tenant, ic.id(), URGENCIAS).orElseThrow().closedAt();

        assertThat(segundoIntento).isEqualTo(primerCierre);
        assertThat(getUseCase.execute(tenant, ic.id(), URGENCIAS).orElseThrow().status())
                .isEqualTo(InterconsultationStatus.CLOSED);
    }
}
