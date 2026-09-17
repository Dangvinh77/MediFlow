package com.mediflow.organization.web.dto.request;

import jakarta.validation.constraints.NotNull;

public record UpdateAccountStatusRequest(
        @NotNull(message = "Active status must be provided")
        Boolean isActive) {
}
