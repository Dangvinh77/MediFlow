package com.mediflow.surgery.infrastructure.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.UUID;

/** The locked internal Patient lookup projection, not the public Patient DTO. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PatientLookupResponse(Boolean exists, UUID patientId) {
}
