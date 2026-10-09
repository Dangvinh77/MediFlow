package com.mediflow.organization.web.dto.request;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record ChangeStaffDepartmentRequest(
        @NotNull(message = "New department ID must not be null")
        UUID newDepartmentId) {
}
