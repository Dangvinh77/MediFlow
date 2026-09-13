package com.mediflow.organization.web.dto.request;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public class ChangeStaffDepartmentRequest {

    @NotNull(message = "New department ID must not be null")
    private UUID newDepartmentId;

    public ChangeStaffDepartmentRequest() {
    }

    public UUID getNewDepartmentId() {
        return newDepartmentId;
    }

    public void setNewDepartmentId(UUID newDepartmentId) {
        this.newDepartmentId = newDepartmentId;
    }
}