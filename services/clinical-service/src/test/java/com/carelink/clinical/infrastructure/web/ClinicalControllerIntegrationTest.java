package com.carelink.clinical.infrastructure.web;

import com.carelink.clinical.ClinicalServiceApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests de seguridad y reglas API para clinical-service.
 */
@SpringBootTest(classes = ClinicalServiceApplication.class)
@org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
public class ClinicalControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void receptionistCannotReadEncounter() throws Exception {
        final UUID tenantId = UUID.randomUUID();
        final UUID physicianId = UUID.randomUUID();

        final UUID patientId =
                createPatient(tenantId, physicianId, "PHYSICIAN");
        final UUID encounterId =
                createEncounter(tenantId, physicianId, patientId);

        mockMvc.perform(
                MockMvcRequestBuilders.get(
                        "/api/v1/patients/{id}/encounters/{eid}",
                        patientId,
                        encounterId)
                        .header("X-Tenant-Id", tenantId)
                        .header("X-User-Id", UUID.randomUUID())
                        .header("X-User-Role", "RECEPTIONIST")
        ).andExpect(MockMvcResultMatchers.status().isForbidden());
    }

    @Test
    void crossTenantPatientAccessReturnsForbidden() throws Exception {
        final UUID tenantA = UUID.randomUUID();
        final UUID tenantB = UUID.randomUUID();
        final UUID physicianId = UUID.randomUUID();

        final UUID patientId =
                createPatient(tenantA, physicianId, "PHYSICIAN");

        mockMvc.perform(
                MockMvcRequestBuilders.get("/api/v1/patients/{id}", patientId)
                        .header("X-Tenant-Id", tenantB)
                        .header("X-User-Id", physicianId)
                        .header("X-User-Role", "PHYSICIAN")
        ).andExpect(MockMvcResultMatchers.status().isForbidden());
    }

    @Test
    void updateSignedEncounterReturnsConflict() throws Exception {
        final UUID tenantId = UUID.randomUUID();
        final UUID physicianId = UUID.randomUUID();

        final UUID patientId =
                createPatient(tenantId, physicianId, "PHYSICIAN");
        final UUID encounterId =
                createEncounter(tenantId, physicianId, patientId);

        mockMvc.perform(
                MockMvcRequestBuilders.post(
                        "/api/v1/patients/{id}/encounters/{eid}/sign",
                        patientId,
                        encounterId)
                        .header("X-Tenant-Id", tenantId)
                        .header("X-User-Id", physicianId)
                        .header("X-User-Role", "PHYSICIAN")
        ).andExpect(MockMvcResultMatchers.status().isOk());

        mockMvc.perform(
                MockMvcRequestBuilders.put(
                        "/api/v1/patients/{id}/encounters/{eid}",
                        patientId,
                        encounterId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Tenant-Id", tenantId)
                        .header("X-User-Id", physicianId)
                        .header("X-User-Role", "PHYSICIAN")
                        .content(
                                encounterPayload(
                                        "Updated",
                                        "Exam",
                                        "Plan",
                                        "Follow"
                                )
                        )
        ).andExpect(MockMvcResultMatchers.status().isConflict());
    }

    @Test
        void gdprCoRequestReturnsRetentionDecision() throws Exception {
        final UUID tenantId = UUID.randomUUID();
        final UUID physicianId = UUID.randomUUID();
        final UUID patientId =
                createPatient(tenantId, physicianId, "PHYSICIAN");

        mockMvc.perform(
                MockMvcRequestBuilders.post(
                        "/api/v1/patients/{id}/gdpr-request",
                        patientId
                )
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Tenant-Id", tenantId)
                        .header("X-User-Id", physicianId)
                        .header("X-User-Role", "TENANT_ADMIN")
                        .content(gdprPayload("ERASURE", "CO", true))
        ).andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.result")
                        .value("RETAINED"));
    }

    @Test
    void gdprEuErasureRequiresExplicitConfirmation() throws Exception {
        final UUID tenantId = UUID.randomUUID();
        final UUID physicianId = UUID.randomUUID();
        final UUID patientId =
                createPatient(tenantId, physicianId, "PHYSICIAN");

        mockMvc.perform(
                MockMvcRequestBuilders.post(
                        "/api/v1/patients/{id}/gdpr-request",
                        patientId
                )
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Tenant-Id", tenantId)
                        .header("X-User-Id", physicianId)
                        .header("X-User-Role", "TENANT_ADMIN")
                        .content(gdprPayload("ERASURE", "EU", false))
        ).andExpect(MockMvcResultMatchers.status().isBadRequest());
    }

    @Test
    void gdprEuErasurePseudonymizesPatientIdentity() throws Exception {
        final UUID tenantId = UUID.randomUUID();
        final UUID physicianId = UUID.randomUUID();
        final UUID patientId =
                createPatient(tenantId, physicianId, "PHYSICIAN");

        mockMvc.perform(
                MockMvcRequestBuilders.post(
                        "/api/v1/patients/{id}/gdpr-request",
                        patientId
                )
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Tenant-Id", tenantId)
                        .header("X-User-Id", physicianId)
                        .header("X-User-Role", "TENANT_ADMIN")
                        .content(gdprPayload("ERASURE", "EU", true))
        ).andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.result")
                        .value("PSEUDONYMIZED"));

        mockMvc.perform(
                MockMvcRequestBuilders.get("/api/v1/patients/{id}", patientId)
                        .header("X-Tenant-Id", tenantId)
                        .header("X-User-Id", physicianId)
                        .header("X-User-Role", "PHYSICIAN")
        ).andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.fullName")
                        .value("PSEUDONYMIZED"));
    }

    private UUID createPatient(final UUID tenantId,
                               final UUID userId,
                               final String role) throws Exception {
        final MvcResult result = mockMvc.perform(
                MockMvcRequestBuilders.post("/api/v1/patients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Tenant-Id", tenantId)
                        .header("X-User-Id", userId)
                        .header("X-User-Role", role)
                        .content(patientPayload())
        ).andExpect(MockMvcResultMatchers.status().isOk()).andReturn();

        final String body = result.getResponse().getContentAsString();
        final String idValue = extractJsonValue(body, "id");
        return UUID.fromString(idValue);
    }

    private UUID createEncounter(final UUID tenantId,
                                 final UUID userId,
                                 final UUID patientId) throws Exception {
        final MvcResult result = mockMvc.perform(
                MockMvcRequestBuilders.post(
                        "/api/v1/patients/{id}/encounters",
                        patientId
                )
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Tenant-Id", tenantId)
                        .header("X-User-Id", userId)
                        .header("X-User-Role", "PHYSICIAN")
                        .content(
                                encounterPayload(
                                        "Headache",
                                        "Normal",
                                        "Plan",
                                        "Rest"
                                )
                        )
        ).andExpect(MockMvcResultMatchers.status().isOk()).andReturn();

        final String body = result.getResponse().getContentAsString();
        final String idValue = extractJsonValue(body, "id");
        return UUID.fromString(idValue);
    }

    private String patientPayload() {
        return "{"
                + "\"fullName\":\"Ada Lovelace\","
                + "\"documentType\":\"CC\","
                + "\"documentValue\":\"123456789\","
                + "\"bloodType\":\"O_POSITIVE\","
                + "\"phone\":\"3001234567\","
                + "\"email\":\"ada@example.com\","
                + "\"emergencyContact\":\"Charles\""
                + "}";
    }

    private String encounterPayload(final String complaint,
                                    final String exam,
                                    final String plan,
                                    final String follow) {
        return "{"
                + "\"chiefComplaint\":\"" + complaint + "\","
                + "\"physicalExam\":\"" + exam + "\","
                + "\"treatmentPlan\":\"" + plan + "\","
                + "\"followUpInstructions\":\"" + follow + "\""
                + "}";
    }

        private String gdprPayload(final String requestType,
                               final String jurisdiction,
                               final boolean confirmed) {
                return "{"
                + "\"requestType\":\"" + requestType + "\","
                + "\"jurisdiction\":\"" + jurisdiction + "\","
                                + "\"confirmed\":" + confirmed
                                + "}";
        }

    private String extractJsonValue(final String json,
                                    final String key) {
        final String token = "\"" + key + "\":\"";
        final int start = json.indexOf(token);
        assertThat(start).isGreaterThanOrEqualTo(0);
        final int valueStart = start + token.length();
        final int valueEnd = json.indexOf('"', valueStart);
        assertThat(valueEnd).isGreaterThan(valueStart);
        return json.substring(valueStart, valueEnd);
    }
}
