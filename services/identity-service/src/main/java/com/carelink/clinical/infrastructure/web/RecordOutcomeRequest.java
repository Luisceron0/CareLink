package com.carelink.clinical.infrastructure.web;

public class RecordOutcomeRequest {
    private String nocCode;
    private Integer effectiveness;
    private String notes;

    public RecordOutcomeRequest() {}

    public String getNocCode() { return nocCode; }
    public void setNocCode(String nocCode) { this.nocCode = nocCode; }
    public Integer getEffectiveness() { return effectiveness; }
    public void setEffectiveness(Integer effectiveness) { this.effectiveness = effectiveness; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
}
