package com.carelink.clinical;

import com.carelink.clinical.application.usecase.CheckPrescriptionConflictsUseCase;
import com.carelink.clinical.application.usecase.DispenseMedicationUseCase;
import com.carelink.clinical.application.usecase.GetAdherenceUseCase;
import com.carelink.clinical.application.usecase.GetLabOrderUseCase;
import com.carelink.clinical.application.usecase.IssuePrescriptionUseCase;
import com.carelink.clinical.application.usecase.OrderLabTestUseCase;
import com.carelink.clinical.application.usecase.RecordLabResultUseCase;
import com.carelink.clinical.application.usecase.RegisterPatientUseCase;
import com.carelink.clinical.domain.AdherenceIndex;
import com.carelink.clinical.domain.CriticalValueNotification;
import com.carelink.clinical.domain.LabOrder;
import com.carelink.clinical.domain.Patient;
import com.carelink.clinical.domain.Prescription;
import com.carelink.clinical.domain.PrescriptionConflict;
import com.carelink.clinical.domain.port.LabRepository;
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

/** FR-CLN-11, FR-CLN-12 contra PostgreSQL real. */
@SpringBootTest(classes = com.carelink.identity.Application.class, properties = {
        "carelink.demo-mode=true",
        "carelink.app-env=test"
})
class LabAndPharmacyIT {

    private static final ServiceScope URGENCIAS = ServiceScope.of("Urgencias");

    @Autowired private SchemaProvisioner schemaProvisioner;
    @Autowired private RegisterPatientUseCase registerPatientUseCase;
    @Autowired private OrderLabTestUseCase orderLabTestUseCase;
    @Autowired private RecordLabResultUseCase recordLabResultUseCase;
    @Autowired private GetLabOrderUseCase getLabOrderUseCase;
    @Autowired private LabRepository labRepository;
    @Autowired private IssuePrescriptionUseCase issuePrescriptionUseCase;
    @Autowired private DispenseMedicationUseCase dispenseUseCase;
    @Autowired private GetAdherenceUseCase adherenceUseCase;
    @Autowired private CheckPrescriptionConflictsUseCase conflictsUseCase;
    @Autowired @Qualifier("adminJdbcTemplate") private JdbcTemplate adminJdbcTemplate;

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        EmbeddedPostgresSupport.registerDynamicProperties(registry, "labpharmacy");
    }

    @Test
    @DisplayName("FR-CLN-11 — un resultado CRÍTICO notifica al médico solicitante; uno normal no")
    void criticalResultNotifiesOrderingPhysician() {
        TenantSlug tenant = new TenantSlug("labclinic");
        schemaProvisioner.provisionSchema(tenant);

        UUID patientId = UUID.randomUUID();
        UUID physicianId = UUID.randomUUID();
        UUID labTechId = UUID.randomUUID();

        LabOrder critica = orderLabTestUseCase.execute(tenant, patientId, UUID.randomUUID(), physicianId,
                "POT", "Potasio sérico", "Urgencias");
        LabOrder normal = orderLabTestUseCase.execute(tenant, patientId, UUID.randomUUID(), physicianId,
                "HB", "Hemoglobina", "Urgencias");

        // Contrapeso primero: un resultado NO crítico no debe generar notificación. Sin
        // esto, un sistema que notificara SIEMPRE pasaría la mitad importante del test.
        Optional<CriticalValueNotification> sinNotificar = recordLabResultUseCase.execute(
                tenant, normal.id(), "13.5", "g/dL", false, labTechId, URGENCIAS);
        assertThat(sinNotificar).isEmpty();
        assertThat(labRepository.findPendingNotifications(tenant, physicianId)).isEmpty();

        Optional<CriticalValueNotification> notificacion = recordLabResultUseCase.execute(
                tenant, critica.id(), "7.1", "mEq/L", true, labTechId, URGENCIAS);
        assertThat(notificacion).isPresent();
        assertThat(notificacion.get().notifyUserId())
                .as("se notifica a quien PIDIÓ el estudio, no a quien cargó el resultado")
                .isEqualTo(physicianId);

        List<CriticalValueNotification> pendientes = labRepository.findPendingNotifications(tenant, physicianId);
        assertThat(pendientes).hasSize(1);
        assertThat(pendientes.get(0).isPending()).isTrue();

        // Acusar recibo la saca de pendientes; y solo puede hacerlo el destinatario.
        assertThat(labRepository.acknowledgeNotification(tenant, pendientes.get(0).id(), UUID.randomUUID()))
                .as("otro usuario no puede acusar recibo por el médico responsable").isFalse();
        assertThat(labRepository.acknowledgeNotification(tenant, pendientes.get(0).id(), physicianId)).isTrue();
        assertThat(labRepository.findPendingNotifications(tenant, physicianId)).isEmpty();

        // El valor del resultado se cifra en reposo.
        String stored = adminJdbcTemplate.queryForObject(
                "SELECT result_value FROM tenant_labclinic.lab_orders WHERE id = ?", String.class, critica.id());
        assertThat(stored).isNotEqualTo("7.1");
        assertThat(getLabOrderUseCase.execute(tenant, critica.id(), URGENCIAS).orElseThrow().resultValue())
                .isEqualTo("7.1");
    }

    @Test
    @DisplayName("FR-CLN-11 — cargar el resultado dos veces no sobreescribe el primero")
    void resultIsWriteOnce() {
        TenantSlug tenant = new TenantSlug("labonce");
        schemaProvisioner.provisionSchema(tenant);

        LabOrder order = orderLabTestUseCase.execute(tenant, UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), "GLU", "Glucemia", "Urgencias");

        recordLabResultUseCase.execute(tenant, order.id(), "98", "mg/dL", false, UUID.randomUUID(), URGENCIAS);
        recordLabResultUseCase.execute(tenant, order.id(), "500", "mg/dL", true, UUID.randomUUID(), URGENCIAS);

        assertThat(getLabOrderUseCase.execute(tenant, order.id(), URGENCIAS).orElseThrow().resultValue())
                .as("sobreescribir un resultado emitido es corregir una historia clínica, no un UPDATE silencioso")
                .isEqualTo("98");
    }

    @Test
    @DisplayName("FR-CLN-12 — dispensación e índice de adherencia")
    void dispensationAndAdherenceIndex() {
        TenantSlug tenant = new TenantSlug("pharmacyclinic");
        schemaProvisioner.provisionSchema(tenant);

        UUID patientId = UUID.randomUUID();
        Prescription rx = issuePrescriptionUseCase.execute(
                tenant, patientId, UUID.randomUUID(), null, UUID.randomUUID(),
                "Amoxicilina", "500 mg", "Con alimentos", "Cada 8 horas", 7, "VO", "Penicilinas", 21,
                "Urgencias");

        // Sin dispensar nada: 0 de 21.
        AdherenceIndex inicial = adherenceUseCase.execute(tenant, rx.id(), URGENCIAS).orElseThrow();
        assertThat(inicial.prescribedDoses()).isEqualTo(21);
        assertThat(inicial.dispensedDoses()).isZero();
        assertThat(inicial.ratio()).isZero();

        assertThat(dispenseUseCase.execute(tenant, rx.id(), patientId, UUID.randomUUID(), 14,
                "Urgencias", URGENCIAS)).isTrue();
        assertThat(dispenseUseCase.execute(tenant, rx.id(), patientId, UUID.randomUUID(), 7,
                "Urgencias", URGENCIAS)).isTrue();

        AdherenceIndex completa = adherenceUseCase.execute(tenant, rx.id(), URGENCIAS).orElseThrow();
        assertThat(completa.dispensedDoses()).as("suma de las dispensaciones").isEqualTo(21);
        assertThat(completa.ratio()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("FR-CLN-12 — sin total de dosis, la adherencia es NO CALCULABLE, no 0%")
    void adherenceIsUncomputableRatherThanZeroWhenTotalIsMissing() {
        TenantSlug tenant = new TenantSlug("pharmacynototal");
        schemaProvisioner.provisionSchema(tenant);

        Prescription rx = issuePrescriptionUseCase.execute(
                tenant, UUID.randomUUID(), UUID.randomUUID(), null, UUID.randomUUID(),
                "Ibuprofeno", "400 mg", null, null, null, "VO", "AINEs", null, "Urgencias");

        AdherenceIndex a = adherenceUseCase.execute(tenant, rx.id(), URGENCIAS).orElseThrow();
        // "no se registró el total" y "el paciente no tomó nada" son afirmaciones
        // clínicas distintas; reportar 0% diría la segunda.
        assertThat(a.isCalculable()).isFalse();
        assertThat(a.ratio()).isNull();
    }

    @Test
    @DisplayName("FR-CLN-12 — el conflicto de alergia ADVIERTE y no bloquea: la prescripción se crea igual")
    void allergyConflictWarnsButDoesNotBlock() {
        TenantSlug tenant = new TenantSlug("conflictclinic");
        schemaProvisioner.provisionSchema(tenant);

        Patient patient = registerPatientUseCase.execute(
                tenant, "Luis Gómez", DocumentType.CEDULA_CIUDADANIA, "1020304055",
                LocalDate.of(1980, 1, 1), Sex.MALE, BloodType.A_POSITIVE,
                List.of("Penicilina", "Polen"), "Urgencias");

        List<PrescriptionConflict> conflictos = conflictsUseCase.execute(
                tenant, patient.id(), "Penicilina G sódica", "Penicilinas", URGENCIAS);

        assertThat(conflictos).isNotEmpty();
        assertThat(conflictos).anyMatch(c -> c.type() == PrescriptionConflict.Type.ALLERGY);

        // Lo esencial de FR-CLN-12: detectar el conflicto NO impide prescribir. El
        // sistema informa; el criterio clínico decide.
        Prescription rx = issuePrescriptionUseCase.execute(
                tenant, patient.id(), UUID.randomUUID(), null, UUID.randomUUID(),
                "Penicilina G sódica", "1 MU", null, "Cada 6 horas", 5, "IV", "Penicilinas", 20, "Urgencias");
        assertThat(rx.id()).isNotNull();

        // Y ahora aparece también el conflicto de "misma clase activa".
        List<PrescriptionConflict> segunda = conflictsUseCase.execute(
                tenant, patient.id(), "Ampicilina", "Penicilinas", URGENCIAS);
        assertThat(segunda).anyMatch(c -> c.type() == PrescriptionConflict.Type.ACTIVE_SAME_CLASS);
    }

    @Test
    @DisplayName("un paciente sin alergias ni prescripciones previas no genera conflictos")
    void noConflictsForCleanPatient() {
        TenantSlug tenant = new TenantSlug("noconflict");
        schemaProvisioner.provisionSchema(tenant);

        Patient patient = registerPatientUseCase.execute(
                tenant, "Sin Alergias", DocumentType.CEDULA_CIUDADANIA, "1020304066",
                LocalDate.of(1995, 6, 6), Sex.FEMALE, BloodType.O_NEGATIVE, List.of(), "Urgencias");

        // Contrapeso del test anterior: si detectConflicts devolviera siempre algo, el
        // test de arriba pasaría sin probar que la detección funciona.
        assertThat(conflictsUseCase.execute(tenant, patient.id(), "Paracetamol", "Analgésicos", URGENCIAS))
                .isEmpty();
    }
}
