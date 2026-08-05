package com.carelink.identity.domain.port;

import com.carelink.identity.domain.audit.AuditEntry;

/** Puerto de escritura del audit trail (FR-CLN-13). Un solo método: append-only. */
public interface AuditEntryPort {
    void record(AuditEntry entry);
}
