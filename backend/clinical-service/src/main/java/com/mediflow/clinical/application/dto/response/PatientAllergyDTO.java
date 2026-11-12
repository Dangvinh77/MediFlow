package com.mediflow.clinical.application.dto.response;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;

public record PatientAllergyDTO(
        UUID allergyId,
        String substance,
        @JsonInclude(JsonInclude.Include.NON_NULL) String reaction,
        String severity,
        String status,
        UUID recordedByStaffId,
        Instant recordedAt,
        @JsonInclude(JsonInclude.Include.NON_NULL) UUID sourceRecordId,
        @JsonInclude(JsonInclude.Include.NON_NULL) String statusReason,
        @JsonInclude(JsonInclude.Include.NON_NULL) UUID statusChangedByStaffId,
        @JsonInclude(JsonInclude.Include.NON_NULL) Instant statusChangedAt
) {
}
