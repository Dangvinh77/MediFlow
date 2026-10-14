package com.mediflow.organization.web.dto.request;

import com.mediflow.organization.domain.model.DepartmentType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateDepartmentRequest(
        @NotBlank(message = "Department name must not be blank")
        @Size(max = 100, message = "Department name must not exceed 100 characters")
        String departmentName,
        @NotBlank(message = "Abbreviation must not be blank")
        @Size(max = 20, message = "Abbreviation must not exceed 20 characters")
        String abbreviation,
        @NotNull(message = "Department type must not be null")
        DepartmentType departmentType,
        @Size(max = 255, message = "Location must not exceed 255 characters")
        String location) {
}
