package com.mediflow.billing.application.port.in;

import com.mediflow.billing.application.dto.command.ReconcileAdmissionCommand;
import com.mediflow.billing.application.event.LedgerIntegrationEvent.SettlementReconciledPayload;

public interface ReconcileAdmissionUseCase {
    SettlementReconciledPayload reconcile(ReconcileAdmissionCommand command);
}
