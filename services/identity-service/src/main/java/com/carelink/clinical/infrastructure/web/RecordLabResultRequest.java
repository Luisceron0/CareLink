package com.carelink.clinical.infrastructure.web;

public class RecordLabResultRequest {
    private String value;
    private String units;
    private Boolean criticalValue;

    public RecordLabResultRequest() {}

    public String getValue() { return value; }
    public void setValue(String v) { this.value = v; }
    public String getUnits() { return units; }
    public void setUnits(String v) { this.units = v; }
    public Boolean getCriticalValue() { return criticalValue; }
    public void setCriticalValue(Boolean v) { this.criticalValue = v; }
}
