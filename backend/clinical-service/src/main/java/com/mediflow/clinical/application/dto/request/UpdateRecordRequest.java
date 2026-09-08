package com.mediflow.clinical.application.dto.request;

import jakarta.validation.constraints.Size;

public record UpdateRecordRequest(
        @Size(max = 4000) String symptoms
) {}
