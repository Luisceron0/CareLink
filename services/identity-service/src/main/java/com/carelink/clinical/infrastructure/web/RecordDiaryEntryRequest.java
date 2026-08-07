package com.carelink.clinical.infrastructure.web;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public class RecordDiaryEntryRequest {
    private UUID patientId;
    private String entryDate;
    private String shift;
    private String observations;
    private List<VitalSignsPayload> vitalSigns;
    private List<InterventionPayload> interventions;

    public RecordDiaryEntryRequest() {}

    public UUID getPatientId() { return patientId; }
    public void setPatientId(UUID patientId) { this.patientId = patientId; }
    public String getEntryDate() { return entryDate; }
    public void setEntryDate(String entryDate) { this.entryDate = entryDate; }
    public String getShift() { return shift; }
    public void setShift(String shift) { this.shift = shift; }
    public String getObservations() { return observations; }
    public void setObservations(String observations) { this.observations = observations; }
    public List<VitalSignsPayload> getVitalSigns() { return vitalSigns; }
    public void setVitalSigns(List<VitalSignsPayload> vitalSigns) { this.vitalSigns = vitalSigns; }
    public List<InterventionPayload> getInterventions() { return interventions; }
    public void setInterventions(List<InterventionPayload> interventions) { this.interventions = interventions; }

    public static class VitalSignsPayload {
        private Integer systolicMmHg;
        private Integer diastolicMmHg;
        private Integer heartRateBpm;
        private Integer respiratoryRate;
        private BigDecimal temperatureCelsius;
        private Integer oxygenSaturation;

        public VitalSignsPayload() {}

        public Integer getSystolicMmHg() { return systolicMmHg; }
        public void setSystolicMmHg(Integer v) { this.systolicMmHg = v; }
        public Integer getDiastolicMmHg() { return diastolicMmHg; }
        public void setDiastolicMmHg(Integer v) { this.diastolicMmHg = v; }
        public Integer getHeartRateBpm() { return heartRateBpm; }
        public void setHeartRateBpm(Integer v) { this.heartRateBpm = v; }
        public Integer getRespiratoryRate() { return respiratoryRate; }
        public void setRespiratoryRate(Integer v) { this.respiratoryRate = v; }
        public BigDecimal getTemperatureCelsius() { return temperatureCelsius; }
        public void setTemperatureCelsius(BigDecimal v) { this.temperatureCelsius = v; }
        public Integer getOxygenSaturation() { return oxygenSaturation; }
        public void setOxygenSaturation(Integer v) { this.oxygenSaturation = v; }
    }

    public static class InterventionPayload {
        private String nandaCode;
        private String nicCode;
        private String diagnosisCie10;
        private String description;

        public InterventionPayload() {}

        public String getNandaCode() { return nandaCode; }
        public void setNandaCode(String v) { this.nandaCode = v; }
        public String getNicCode() { return nicCode; }
        public void setNicCode(String v) { this.nicCode = v; }
        public String getDiagnosisCie10() { return diagnosisCie10; }
        public void setDiagnosisCie10(String v) { this.diagnosisCie10 = v; }
        public String getDescription() { return description; }
        public void setDescription(String v) { this.description = v; }
    }
}
