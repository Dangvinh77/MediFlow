package com.mediflow.organization.web.dto.response;

import java.util.UUID;

public class CreateDepartmentResponse {

    private UUID departmentId;

    public CreateDepartmentResponse(UUID departmentId) {
        this.departmentId = departmentId;
    }

    public UUID getDepartmentId() {
        return departmentId;
    }
}