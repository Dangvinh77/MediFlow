package com.mediflow.patient.application.port.in;

import com.mediflow.patient.application.dto.response.PatientDemographicsDTO;
import com.mediflow.patient.application.dto.response.PatientLookupDTO;

import java.util.UUID;

/** Service-only patient identity lookup port. */
public interface ReadPatientIdentityUseCase {

    PatientLookupDTO exists(UUID patientId);

    /** Sex and date of birth for Lab reference ranges; PatientNotFoundException when unknown. */
    PatientDemographicsDTO demographics(UUID patientId);
}
