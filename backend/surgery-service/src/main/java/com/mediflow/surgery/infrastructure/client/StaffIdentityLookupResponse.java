package com.mediflow.surgery.infrastructure.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.UUID;

/** Organization-owned phase-1 staff lookup wire shape. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record StaffIdentityLookupResponse(
        Boolean exists,
        Boolean active,
        String jobTitle,
        UUID departmentId) {
}
