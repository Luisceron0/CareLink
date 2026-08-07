package com.carelink.clinical.infrastructure.web;

public class IssuePrescriptionRequest {
    private String medication;
    private String dosage;
    private String instructions;
    private String frequency;
    private Integer durationDays;
    private String route;
    private String medicationClass;
    private Integer totalDoses;

    public IssuePrescriptionRequest() {}

    public String getFrequency() { return frequency; }
    public void setFrequency(String v) { this.frequency = v; }
    public Integer getDurationDays() { return durationDays; }
    public void setDurationDays(Integer v) { this.durationDays = v; }
    public String getRoute() { return route; }
    public void setRoute(String v) { this.route = v; }
    public String getMedicationClass() { return medicationClass; }
    public void setMedicationClass(String v) { this.medicationClass = v; }
    public Integer getTotalDoses() { return totalDoses; }
    public void setTotalDoses(Integer v) { this.totalDoses = v; }

    public String getMedication() { return medication; }
    public void setMedication(String v) { this.medication = v; }
    public String getDosage() { return dosage; }
    public void setDosage(String v) { this.dosage = v; }
    public String getInstructions() { return instructions; }
    public void setInstructions(String v) { this.instructions = v; }
}
