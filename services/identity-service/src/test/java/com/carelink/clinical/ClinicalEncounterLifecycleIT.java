package com.carelink.clinical;

import com.carelink.clinical.application.usecase.GetEncounterUseCase;
import com.carelink.clinical.application.usecase.RegisterEncounterUseCase;
import com.carelink.clinical.application.usecase.SignEncounterUseCase;
import com.carelink.clinical.application.usecase.UpdateEncounterUseCase;
import com.carelink.clinical.domain.ClinicalEncounter;
import com.carelink.clinical.domain.exception.EncounterAlreadySignedException;
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

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * FR-CLN-02, AC-08 — con los beans reales de Spring, mismo motivo que
 * {@code PatientLifecycleIT}: {@code @Auditable} solo se intercepta a través del proxy
 * AOP de beans que Spring administra.
 */
@SpringBootTest(classes = com.carelink.identity.Application.class, properties = {
        "carelink.demo-mode=true",
        "carelink.app-env=test"
})
class ClinicalEncounterLifecycleIT {

    @Autowired
    private SchemaProvisioner schemaProvisioner;

    @Autowired
    private RegisterEncounterUseCase registerEncounterUseCase;

    @Autowired
    private UpdateEncounterUseCase updateEncounterUseCase;

    @Autowired
    private SignEncounterUseCase signEncounterUseCase;

    @Autowired
    private GetEncounterUseCase getEncounterUseCase;

    @Autowired
    @Qualifier("adminJdbcTemplate")
    private JdbcTemplate adminJdbcTemplate;

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        EmbeddedPostgresSupport.registerDynamicProperties(registry, "encounterlifecycle");
    }

    @Test
    @DisplayName("AC-08 — un encounter firmado rechaza modificaciones con 409 (EncounterAlreadySignedException), uno sin firmar se puede editar")
    void signedEncounterRejectsModificationUnsignedDoesNot() {
        TenantSlug tenantSlug = new TenantSlug("encounterclinic");
        schemaProvisioner.provisionSchema(tenantSlug);

        UUID patientId = UUID.randomUUID();
        UUID physicianId = UUID.randomUUID();

        ClinicalEncounter created = registerEncounterUseCase.execute(
                tenantSlug, patientId, physicianId,
                "Dolor torácico de dos días de evolución", "Auscultación sin hallazgos",
                "R07.4", "Reposo, control en 48h", "Reconsultar si empeora");

        assertThat(created.isSigned()).isFalse();

        // Contrapeso, antes de firmar: un encounter SIN firmar se puede editar sin
        // problema — si esto fallara, el test de "firmado rechaza" no probaría nada
        // específico sobre estar firmado, porque ninguna edición pasaría nunca.
        ClinicalEncounter edited = new ClinicalEncounter(
                created.id(), created.patientId(), created.physicianUserId(),
                "Dolor torácico — actualizado", created.examFindings(), created.diagnosisCie10(),
                created.treatmentPlan(), created.followUp(), created.createdAt(), null, null);
        updateEncounterUseCase.execute(tenantSlug, edited);

        Optional<ClinicalEncounter> afterEdit = getEncounterUseCase.execute(tenantSlug, created.id());
        assertThat(afterEdit).isPresent();
        assertThat(afterEdit.get().chiefComplaint()).isEqualTo("Dolor torácico — actualizado");

        // Firmar.
        signEncounterUseCase.execute(tenantSlug, created.id(), physicianId);

        Optional<ClinicalEncounter> afterSign = getEncounterUseCase.execute(tenantSlug, created.id());
        assertThat(afterSign).isPresent();
        assertThat(afterSign.get().isSigned()).isTrue();
        assertThat(afterSign.get().signedByUserId()).isEqualTo(physicianId);

        // AC-08: ahora CUALQUIER edición se rechaza — el trigger de la base, no una
        // validación de aplicación que alguien podría saltear con acceso directo.
        ClinicalEncounter attemptAfterSign = new ClinicalEncounter(
                created.id(), created.patientId(), created.physicianUserId(),
                "Intento de alterar un encounter firmado", created.examFindings(), created.diagnosisCie10(),
                created.treatmentPlan(), created.followUp(), created.createdAt(), null, null);
        assertThatThrownBy(() -> updateEncounterUseCase.execute(tenantSlug, attemptAfterSign))
                .isInstanceOf(EncounterAlreadySignedException.class);

        // Re-firmar tampoco: ya estaba firmado.
        assertThatThrownBy(() -> signEncounterUseCase.execute(tenantSlug, created.id(), physicianId))
                .isInstanceOf(EncounterAlreadySignedException.class);

        // Y el contenido NO cambió — el intento de "Intento de alterar..." no se coló.
        Optional<ClinicalEncounter> afterRejectedUpdate = getEncounterUseCase.execute(tenantSlug, created.id());
        assertThat(afterRejectedUpdate.get().chiefComplaint()).isEqualTo("Dolor torácico — actualizado");
    }

    @Test
    @DisplayName("las notas clínicas se cifran en reposo; diagnosis_cie10 queda legible para el Motor de Conocimiento (Sub-fase 4)")
    void clinicalNotesAreEncryptedDiagnosisCodeIsNot() {
        TenantSlug tenantSlug = new TenantSlug("encounterphi");
        schemaProvisioner.provisionSchema(tenantSlug);

        ClinicalEncounter created = registerEncounterUseCase.execute(
                tenantSlug, UUID.randomUUID(), UUID.randomUUID(),
                "Motivo de consulta confidencial", "Hallazgos del examen",
                "J45.9", "Plan de tratamiento", "Seguimiento");

        var row = adminJdbcTemplate.queryForMap(
                "SELECT chief_complaint, exam_findings, diagnosis_cie10 FROM tenant_encounterphi.clinical_encounters WHERE id = ?",
                created.id());

        assertThat((String) row.get("chief_complaint")).doesNotContain("Motivo de consulta confidencial");
        assertThat((String) row.get("exam_findings")).doesNotContain("Hallazgos del examen");
        // diagnosis_cie10 SIN cifrar, a propósito — es un código categórico, no texto
        // libre, y el Motor de Conocimiento necesita poder agruparlo sin descifrar.
        assertThat((String) row.get("diagnosis_cie10")).isEqualTo("J45.9");
    }
}
