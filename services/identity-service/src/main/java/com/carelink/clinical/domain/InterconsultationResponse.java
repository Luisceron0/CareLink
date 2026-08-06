package com.carelink.clinical.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

/** FR-CLN-08 — la opinión del especialista. */
public record InterconsultationResponse(
        UUID id,
        UUID interconsultationId,
        UUID specialistUserId,
        String opinion,
        OffsetDateTime respondedAt
) {}
