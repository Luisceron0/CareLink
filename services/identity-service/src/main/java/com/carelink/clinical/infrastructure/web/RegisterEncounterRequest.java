package com.carelink.clinical.infrastructure.web;

import java.util.UUID;

public class RegisterEncounterRequest {
    private UUID patientId;
    private String chiefComplaint;
    private String examFindings;
    private String diagnosisCie10;
    private String treatmentPlan;
    private String followUp;

    public RegisterEncounterRequest() {}

    public UUID getPatientId() { return patientId; }
    public void setPatientId(UUID patientId) { this.patientId = patientId; }
    public String getChiefComplaint() { return chiefComplaint; }
    public void setChiefComplaint(String chiefComplaint) { this.chiefComplaint = chiefComplaint; }
    public String getExamFindings() { return examFindings; }
    public void setExamFindings(String examFindings) { this.examFindings = examFindings; }
    public String getDiagnosisCie10() { return diagnosisCie10; }
    public void setDiagnosisCie10(String diagnosisCie10) { this.diagnosisCie10 = diagnosisCie10; }
    public String getTreatmentPlan() { return treatmentPlan; }
    public void setTreatmentPlan(String treatmentPlan) { this.treatmentPlan = treatmentPlan; }
    public String getFollowUp() { return followUp; }
    public void setFollowUp(String followUp) { this.followUp = followUp; }
}
