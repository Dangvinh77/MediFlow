package com.mediflow.clinical.application.port.in;

import com.mediflow.clinical.application.dto.request.CreateClinicalLabOrderRequest;
import com.mediflow.clinical.application.dto.response.ClinicalLabOrderDTO;
import java.util.UUID;

public interface CreateClinicalLabOrderUseCase {
    ClinicalLabOrderDTO create(UUID recordId, CreateClinicalLabOrderRequest request);
}
