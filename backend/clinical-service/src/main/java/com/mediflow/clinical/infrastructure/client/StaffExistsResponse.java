package com.mediflow.clinical.infrastructure.client;

import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Minimal Organization response used by Clinical. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record StaffExistsResponse(
        boolean exists,
        boolean eligibleDoctor,
        @JsonAlias("maKhoa") UUID departmentId
) {
}
