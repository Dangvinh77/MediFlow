package com.mediflow.organization.web.dto.request;

import com.mediflow.organization.domain.model.DepartmentType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Adds a room type to one of the three fixed department categories. */
public record RoomTypeRequest(
        @NotBlank @Size(min = 2, max = 100) String name,
        @Size(max = 255) String description,
        @NotNull(message = "Department category is required") DepartmentType departmentType,
        boolean singlePerDepartment) {}
