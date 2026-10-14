package com.mediflow.patient.application.port.in;

import com.mediflow.patient.application.dto.request.UpdatePatientRequest;
import com.mediflow.patient.application.dto.response.PatientDTO;

import java.util.UUID;

public interface UpdatePatientUseCase {
    PatientDTO update(UUID patientId, UpdatePatientRequest request);
}
