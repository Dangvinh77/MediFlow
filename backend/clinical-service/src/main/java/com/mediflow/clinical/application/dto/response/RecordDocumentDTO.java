package com.mediflow.clinical.application.dto.response;

import java.time.Instant;
import java.util.UUID;

/** A result or prescription linked to a visit; {@code afterConclusion} marks what arrived after the visit ended. */
public record RecordDocumentDTO(String type, UUID referenceId, String summary, Instant attachedAt,
        boolean afterConclusion) {}
