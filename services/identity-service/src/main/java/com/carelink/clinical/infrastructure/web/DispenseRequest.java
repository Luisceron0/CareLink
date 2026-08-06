package com.carelink.clinical.infrastructure.web;

import java.util.UUID;

public class DispenseRequest {
    private UUID prescriptionId;
    private UUID patientId;
    private Integer dosesDispensed;

    public DispenseRequest() {}

    public UUID getPrescriptionId() { return prescriptionId; }
    public void setPrescriptionId(UUID v) { this.prescriptionId = v; }
    public UUID getPatientId() { return patientId; }
    public void setPatientId(UUID v) { this.patientId = v; }
    public Integer getDosesDispensed() { return dosesDispensed; }
    public void setDosesDispensed(Integer v) { this.dosesDispensed = v; }
}
