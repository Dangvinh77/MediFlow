package com.mediflow.patient.application.port.in;

import com.mediflow.patient.application.dto.request.CreatePatientRequest;
import com.mediflow.patient.application.dto.response.PatientDTO;

/** Tiếp nhận một bệnh nhân mới. */
public interface CreatePatientUseCase {

    PatientDTO create(CreatePatientRequest request);
}
