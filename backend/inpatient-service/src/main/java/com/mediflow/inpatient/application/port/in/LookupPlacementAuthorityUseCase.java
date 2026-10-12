package com.mediflow.inpatient.application.port.in;

import com.mediflow.inpatient.application.dto.response.PlacementLookupDTO;
import java.util.UUID;

public interface LookupPlacementAuthorityUseCase {
    PlacementLookupDTO lookup(UUID admissionId);
}
