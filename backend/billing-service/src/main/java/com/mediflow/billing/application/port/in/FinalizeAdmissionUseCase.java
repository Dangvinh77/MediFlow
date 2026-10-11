package com.mediflow.billing.application.port.in;

import com.mediflow.billing.application.dto.command.FinalizeAdmissionCommand;
import com.mediflow.billing.application.event.LedgerIntegrationEvent.FinalSettlementRecognitionPayload;

public interface FinalizeAdmissionUseCase {
    FinalSettlementRecognitionPayload finalizeAdmission(FinalizeAdmissionCommand command);
}
