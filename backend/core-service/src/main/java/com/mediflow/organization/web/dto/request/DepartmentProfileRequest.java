package com.mediflow.organization.web.dto.request;

import com.mediflow.organization.domain.model.DepartmentProfile;

public record DepartmentProfileRequest(
        @jakarta.validation.constraints.Size(max = 2000) String description,
        @jakarta.validation.constraints.Size(max = 30) @jakarta.validation.constraints.Pattern(regexp = "[0-9+() .-]{3,30}") String phoneNumber,
        @jakarta.validation.constraints.Size(max = 100) @jakarta.validation.constraints.Email String email) {
    public DepartmentProfile toDomain() { return new DepartmentProfile(description, phoneNumber, email); }
}
