package com.mediflow.billing.application.service;

import com.mediflow.billing.application.dto.command.CloseAdmissionChargesCommand;
import com.mediflow.billing.application.port.in.CloseAdmissionChargesUseCase;
import com.mediflow.billing.application.port.out.AdmissionChargeCloseStorePort;
import java.time.Clock;

/** Closes charge intake only. Payment, allocation, settlement and recognition remain independent. */
public final class AdmissionChargeCloseService implements CloseAdmissionChargesUseCase {
    private final AdmissionChargeCloseStorePort store;
    private final Clock clock;

    public AdmissionChargeCloseService(AdmissionChargeCloseStorePort store, Clock clock) {
        this.store = store;
        this.clock = clock;
    }

    @Override public void close(CloseAdmissionChargesCommand command) {
        if (command.approvedAt().isAfter(clock.instant())) {
            throw new IllegalArgumentException("Future medical discharge cannot close financial intake");
        }
        store.acceptAndClose(command);
    }
}
