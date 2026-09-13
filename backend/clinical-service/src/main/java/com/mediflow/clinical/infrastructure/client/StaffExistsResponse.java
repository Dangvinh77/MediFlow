package com.mediflow.clinical.infrastructure.client;

import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonAlias;

/** Minimal Organization response used by Clinical. */
public record StaffExistsResponse(
        boolean exists,
        @JsonAlias("maKhoa") UUID departmentId
) {
}
