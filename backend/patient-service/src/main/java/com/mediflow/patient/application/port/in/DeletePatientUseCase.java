package com.mediflow.patient.application.port.in;

import java.util.UUID;

public interface DeletePatientUseCase {
    void delete(UUID patientId);
}
