package com.mediflow.patient.application.dto.response;

import java.util.UUID;

/** Minimal service-to-service existence projection. */
public record PatientLookupDTO(boolean exists, UUID patientId) {
}
