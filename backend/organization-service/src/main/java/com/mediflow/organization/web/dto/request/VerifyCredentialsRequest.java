package com.mediflow.organization.web.dto.request;

import jakarta.validation.constraints.NotBlank;

public record VerifyCredentialsRequest(
        @NotBlank(message = "Username must not be blank")
        String username,

        @NotBlank(message = "Password must not be blank")
        String password) {
}
