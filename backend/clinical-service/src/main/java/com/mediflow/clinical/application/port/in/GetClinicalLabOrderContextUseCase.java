package com.mediflow.clinical.application.port.in;

import com.mediflow.clinical.application.dto.response.ClinicalLabOrderContextDTO;
import java.util.UUID;

public interface GetClinicalLabOrderContextUseCase {
    ClinicalLabOrderContextDTO get(UUID sourceOrderId);
}
