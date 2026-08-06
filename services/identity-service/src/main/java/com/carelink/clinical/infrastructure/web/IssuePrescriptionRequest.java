package com.carelink.clinical.infrastructure.web;

public class IssuePrescriptionRequest {
    private String medication;
    private String dosage;
    private String instructions;

    public IssuePrescriptionRequest() {}

    public String getMedication() { return medication; }
    public void setMedication(String v) { this.medication = v; }
    public String getDosage() { return dosage; }
    public void setDosage(String v) { this.dosage = v; }
    public String getInstructions() { return instructions; }
    public void setInstructions(String v) { this.instructions = v; }
}
