package com.mediflow.organization.web.dto.response;

import java.util.UUID;

public class CreateStaffResponse {

    private UUID staffId;

    public CreateStaffResponse(UUID staffId) {
        this.staffId = staffId;
    }

    public UUID getStaffId() {
        return staffId;
    }
}