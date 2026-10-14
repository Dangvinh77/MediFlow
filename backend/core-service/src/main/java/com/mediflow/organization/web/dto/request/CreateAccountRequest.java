package com.mediflow.organization.web.dto.request;

import java.util.UUID;

import com.mediflow.organization.domain.model.Role;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateAccountRequest(
        @NotBlank(message = "Username must not be blank")
        @Size(min = 3, max = 50, message = "Username must contain 3 to 50 characters")
        @Pattern(
                regexp = "^[a-zA-Z0-9._-]+$",
                message = "Username contains invalid characters")
        String username,

        @NotBlank(message = "Password must not be blank")
        @Size(min = 8, max = 72, message = "Password must contain 8 to 72 characters")
        String password,

        UUID staffId,

        UUID patientId,

        @NotNull(message = "Role must not be null")
        Role role) {
}
