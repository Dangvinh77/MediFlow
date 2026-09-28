package com.mediflow.report.application.port.in;

import com.mediflow.report.application.dto.command.carefinance.OperationalProjectionCommand;
import com.mediflow.report.application.dto.command.carefinance.PaymentCompletedCommand;
import com.mediflow.report.application.dto.command.carefinance.PaymentRefundedCommand;
import com.mediflow.report.application.dto.command.carefinance.SettlementCompletedCommand;

/** V2 projection boundary; no Rabbit binding or projection implementation is enabled by this port. */
public interface ProjectCareFinanceEventUseCase {

    void onPaymentCompleted(PaymentCompletedCommand command);

    void onPaymentRefunded(PaymentRefundedCommand command);

    void onSettlementCompleted(SettlementCompletedCommand command);

    void onOperationalEvent(OperationalProjectionCommand command);
}
