package com.carelink.clinical.infrastructure.web;

import java.util.UUID;

public class RegisterAdmissionRequest {
    private UUID patientId;
    private String admissionType;
    private Integer triagePriority;

    public RegisterAdmissionRequest() {}

    public UUID getPatientId() { return patientId; }
    public void setPatientId(UUID patientId) { this.patientId = patientId; }
    public String getAdmissionType() { return admissionType; }
    public void setAdmissionType(String admissionType) { this.admissionType = admissionType; }
    public Integer getTriagePriority() { return triagePriority; }
    public void setTriagePriority(Integer triagePriority) { this.triagePriority = triagePriority; }
}
