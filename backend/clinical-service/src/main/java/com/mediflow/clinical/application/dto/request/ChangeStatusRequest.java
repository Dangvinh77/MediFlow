package com.mediflow.clinical.application.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import com.mediflow.clinical.domain.model.AppointmentStatus;

public record ChangeStatusRequest(
        @NotNull AppointmentStatus status,
        @Size(max = 500) String reason
) {
    public ChangeStatusRequest(AppointmentStatus status) {
        this(status, null);
    }
}
