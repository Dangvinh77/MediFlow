package com.mediflow.patient.application.port.in;

import com.mediflow.patient.application.dto.request.UpdatePatientRequest;
import com.mediflow.patient.application.dto.response.PatientDTO;

import java.util.UUID;

public interface UpdatePatientUseCase {
    PatientDTO update(UUID patientId, UpdatePatientRequest request);

    /** Updates only the contact email for trusted internal account synchronization. */
    PatientDTO synchronizeAccountEmail(UUID patientId, String email);
}
