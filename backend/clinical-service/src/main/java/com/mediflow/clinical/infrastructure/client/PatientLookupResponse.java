package com.mediflow.clinical.infrastructure.client;

import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Minimal Patient identity response consumed by Clinical. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PatientLookupResponse(boolean exists, UUID patientId) {
}
