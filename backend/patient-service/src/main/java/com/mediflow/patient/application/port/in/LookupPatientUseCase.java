package com.mediflow.patient.application.port.in;

import com.mediflow.patient.application.dto.response.PatientLookupDTO;

import java.util.UUID;

public interface LookupPatientUseCase {

    PatientLookupDTO exists(UUID patientId);
}
