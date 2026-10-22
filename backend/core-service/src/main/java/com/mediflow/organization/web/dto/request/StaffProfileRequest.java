package com.mediflow.organization.web.dto.request;

import com.mediflow.organization.domain.model.StaffProfile;

public record StaffProfileRequest(
        @jakarta.validation.constraints.Past java.time.LocalDate dateOfBirth,
        com.mediflow.organization.domain.model.StaffProfile.Gender gender,
        @jakarta.validation.constraints.Size(max = 255) String address,
        java.time.LocalDate hireDate,
        @jakarta.validation.constraints.Size(max = 100) String qualification) {
    public StaffProfile toDomain() { return new StaffProfile(dateOfBirth, gender, address, hireDate, qualification); }
}
