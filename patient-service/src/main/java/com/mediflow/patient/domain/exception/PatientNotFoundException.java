package com.mediflow.patient.domain.exception;

import com.mediflow.common.exception.ResourceNotFoundException;

import java.util.UUID;

/** No patient with that id. Maps to HTTP 404. */
public class PatientNotFoundException extends ResourceNotFoundException {

    public PatientNotFoundException(UUID maBenhNhan) {
        super("PATIENT_NOT_FOUND", "Không tìm thấy bệnh nhân: " + maBenhNhan);
    }
}
