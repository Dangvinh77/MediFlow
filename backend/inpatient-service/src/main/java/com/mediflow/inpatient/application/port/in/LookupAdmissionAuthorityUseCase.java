package com.mediflow.inpatient.application.port.in;

import com.mediflow.inpatient.application.dto.response.AdmissionLookupDTO;
import java.util.UUID;

public interface LookupAdmissionAuthorityUseCase {
    AdmissionLookupDTO lookup(UUID admissionId);
}
