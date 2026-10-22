package com.mediflow.billing.application.port.in;

import com.mediflow.billing.application.dto.response.OutpatientAdjustmentDTO;
import java.util.UUID;

public interface GetOutpatientAdjustmentUseCase {
    OutpatientAdjustmentDTO get(String sourceType, UUID sourceId);
}
