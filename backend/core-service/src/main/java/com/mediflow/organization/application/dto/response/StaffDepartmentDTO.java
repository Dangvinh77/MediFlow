package com.mediflow.organization.application.dto.response;

import java.util.UUID;

/** Minimal unit label for the signed staff member's own profile. */
public record StaffDepartmentDTO(UUID departmentId, String departmentName) { }
