package com.mediflow.lab.application.dto.request;

import jakarta.validation.Valid;

/** Starts a test with clearance, or with an explicit audited emergency approval. */
public record StartLabTestRequest(
        @Valid EmergencyOverrideRequest emergencyOverride
) {}
