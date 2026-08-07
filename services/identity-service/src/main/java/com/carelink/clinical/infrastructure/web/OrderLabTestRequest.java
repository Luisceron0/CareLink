package com.carelink.clinical.infrastructure.web;

import java.util.UUID;

public class OrderLabTestRequest {
    private UUID patientId;
    private UUID encounterId;
    private String testCode;
    private String testName;

    public OrderLabTestRequest() {}

    public UUID getPatientId() { return patientId; }
    public void setPatientId(UUID v) { this.patientId = v; }
    public UUID getEncounterId() { return encounterId; }
    public void setEncounterId(UUID v) { this.encounterId = v; }
    public String getTestCode() { return testCode; }
    public void setTestCode(String v) { this.testCode = v; }
    public String getTestName() { return testName; }
    public void setTestName(String v) { this.testName = v; }
}
