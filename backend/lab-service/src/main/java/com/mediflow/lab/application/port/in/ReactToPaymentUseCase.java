package com.mediflow.lab.application.port.in;

import com.mediflow.lab.application.dto.command.PaymentCompletedCommand;

/** Handles Billing confirmation for explicitly referenced Lab tests. */
public interface ReactToPaymentUseCase {

    void onPaymentCompleted(PaymentCompletedCommand command);
}
