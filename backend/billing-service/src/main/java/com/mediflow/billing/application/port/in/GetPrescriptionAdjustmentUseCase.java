package com.mediflow.billing.application.port.in;

import com.mediflow.billing.application.dto.response.PrescriptionAdjustmentDTO;
import java.util.UUID;

public interface GetPrescriptionAdjustmentUseCase {
    PrescriptionAdjustmentDTO get(UUID prescriptionId);
}
