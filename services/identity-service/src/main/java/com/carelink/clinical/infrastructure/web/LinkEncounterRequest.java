package com.carelink.clinical.infrastructure.web;

import java.util.UUID;

public class LinkEncounterRequest {
    private UUID encounterId;

    public LinkEncounterRequest() {}

    public UUID getEncounterId() { return encounterId; }
    public void setEncounterId(UUID encounterId) { this.encounterId = encounterId; }
}
