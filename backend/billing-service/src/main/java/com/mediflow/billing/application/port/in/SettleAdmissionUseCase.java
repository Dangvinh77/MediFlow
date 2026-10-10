package com.mediflow.billing.application.port.in;

import com.mediflow.billing.application.dto.request.SettleAdmissionRequest;
import com.mediflow.billing.application.dto.response.SettlementDTO;
import java.util.UUID;

public interface SettleAdmissionUseCase {
    SettlementDTO settle(UUID accountId, SettleAdmissionRequest request, UUID actorAccountId, String correlationId);
}
