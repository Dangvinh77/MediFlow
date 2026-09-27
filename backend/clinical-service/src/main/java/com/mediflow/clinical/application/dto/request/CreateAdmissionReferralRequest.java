package com.mediflow.clinical.application.dto.request;

import com.mediflow.clinical.domain.model.AdmissionPriority;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateAdmissionReferralRequest(
        @NotBlank @Size(max = 4000) String diagnosisSummary,
        @NotNull AdmissionPriority priority,
        boolean emergency
) {
}
