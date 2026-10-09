package com.mediflow.clinical.application.port.in;

import com.mediflow.clinical.application.dto.response.PrescriptionContextDTO;
import java.util.UUID;

public interface GetPrescriptionContextUseCase {
    PrescriptionContextDTO get(UUID recordId);
}
