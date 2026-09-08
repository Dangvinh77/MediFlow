package com.mediflow.lab.application.dto.request;

import com.mediflow.lab.domain.model.LabTestStatus;

import jakarta.validation.constraints.NotNull;

/** Request body for a lifecycle transition. Completion still requires aggregate results/date rules. */
public record ChangeStatusRequest(
        @NotNull LabTestStatus status
) {}
