package com.mediflow.clinical.application.dto.request;

import jakarta.validation.constraints.NotNull;
import com.mediflow.clinical.domain.model.AppointmentStatus;

public record ChangeStatusRequest(
        @NotNull AppointmentStatus status
) {}
