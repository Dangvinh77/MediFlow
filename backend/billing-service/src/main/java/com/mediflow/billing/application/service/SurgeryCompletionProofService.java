package com.mediflow.billing.application.service;

import com.mediflow.billing.application.dto.command.CaptureSurgeryCompletionCommand;
import com.mediflow.billing.application.port.in.SurgeryChargeUseCase;
import com.mediflow.billing.application.port.out.SurgeryCompletionStorePort;
import com.mediflow.billing.domain.exception.BillingRuleException;
import java.time.Clock;

public final class SurgeryCompletionProofService {
    private final SurgeryCompletionStorePort store;
    private final SurgeryChargeUseCase charges;
    private final Clock clock;
    public SurgeryCompletionProofService(SurgeryCompletionStorePort store, SurgeryChargeUseCase charges, Clock clock) {
        this.store=store; this.charges=charges; this.clock=clock;
    }
    public void capture(CaptureSurgeryCompletionCommand command) {
        if (command.event().recordedAt().isAfter(clock.instant())) throw new BillingRuleException(
                "BILLING_SURGERY_COMPLETION_FUTURE", "Future Surgery completion cannot close performed charges");
        if (store.lockAndFindReplay(command)) return;
        charges.onSurgeryCompleted(command.event());
        store.save(command);
    }
}
