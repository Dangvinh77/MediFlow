package com.mediflow.clinical.application.dto.response;

import java.time.Instant;

/** Latest accepted correction for one section, projected over the immutable completed record. */
public record EffectiveRecordCorrectionDTO(
        String section,
        String originalText,
        String correctedText,
        String reason,
        Instant appliedAt) {
}
