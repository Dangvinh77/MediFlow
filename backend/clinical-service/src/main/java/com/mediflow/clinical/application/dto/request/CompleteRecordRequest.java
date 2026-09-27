package com.mediflow.clinical.application.dto.request;

import com.mediflow.clinical.domain.model.RecordDisposition;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CompleteRecordRequest(
        @NotNull RecordDisposition disposition,
        @Size(max = 2000) String dispositionNote
) {
}
