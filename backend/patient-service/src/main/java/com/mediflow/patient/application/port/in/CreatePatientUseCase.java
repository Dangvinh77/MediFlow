package com.mediflow.patient.application.port.in;

import com.mediflow.patient.application.dto.request.CreatePatientRequest;
import com.mediflow.patient.application.dto.response.PatientDTO;

public interface CreatePatientUseCase {
    PatientDTO create(CreatePatientRequest request);
}
