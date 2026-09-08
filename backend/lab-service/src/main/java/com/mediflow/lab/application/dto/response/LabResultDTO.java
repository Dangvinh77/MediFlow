package com.mediflow.lab.application.dto.response;

import java.util.UUID;

/** Client-facing result value; value deliberately remains textual. */
public record LabResultDTO(
        UUID resultId,
        String indicator,
        String value,
        String unit,
        String referenceRange
) {}
