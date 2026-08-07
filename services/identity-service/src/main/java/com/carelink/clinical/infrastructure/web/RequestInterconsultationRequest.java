package com.carelink.clinical.infrastructure.web;

import java.util.UUID;

public class RequestInterconsultationRequest {
    private UUID patientId;
    private UUID encounterId;
    private UUID specialistUserId;
    private String question;

    public RequestInterconsultationRequest() {}

    public UUID getPatientId() { return patientId; }
    public void setPatientId(UUID v) { this.patientId = v; }
    public UUID getEncounterId() { return encounterId; }
    public void setEncounterId(UUID v) { this.encounterId = v; }
    public UUID getSpecialistUserId() { return specialistUserId; }
    public void setSpecialistUserId(UUID v) { this.specialistUserId = v; }
    public String getQuestion() { return question; }
    public void setQuestion(String v) { this.question = v; }
}
