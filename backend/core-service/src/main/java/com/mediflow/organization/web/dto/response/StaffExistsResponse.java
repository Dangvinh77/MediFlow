package com.mediflow.organization.web.dto.response;

import java.util.UUID;

public record StaffExistsResponse(
        boolean exists,
        UUID departmentId) {
}
