package com.mediflow.billing.application.port.in;

import java.util.UUID;
import com.mediflow.billing.application.dto.response.AdmissionSettlementPreviewDTO;

public interface GetAdmissionSettlementPreviewUseCase {
    AdmissionSettlementPreviewDTO get(UUID accountId);
}
