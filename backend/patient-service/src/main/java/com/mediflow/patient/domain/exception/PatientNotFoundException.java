package com.mediflow.patient.domain.exception;

import com.mediflow.common.exception.ResourceNotFoundException;

import java.util.UUID;

/** Raised only for the human read endpoint when a patient is absent. */
public class PatientNotFoundException extends ResourceNotFoundException {

    public PatientNotFoundException(UUID patientId) {
        super("PATIENT_NOT_FOUND", "Patient not found: " + patientId);
    }
}
