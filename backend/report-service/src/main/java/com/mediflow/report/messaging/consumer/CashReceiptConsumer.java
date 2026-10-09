package com.mediflow.report.messaging.consumer;

import com.mediflow.common.exception.BusinessRuleException;
import com.mediflow.common.exception.DuplicateResourceException;
import com.mediflow.report.application.port.in.ApplyCashReceiptUseCase;
import com.mediflow.report.application.port.out.CareFinanceWirePort;
import java.time.DateTimeException;
import java.util.Objects;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;

/** Gross inflow evidence only. Return/ACK follows the receipt kernel's committed transaction. */
public final class CashReceiptConsumer {
    private final CareFinanceWirePort decoder;
    private final ApplyCashReceiptUseCase receipts;

    public CashReceiptConsumer(CareFinanceWirePort decoder, ApplyCashReceiptUseCase receipts) {
        this.decoder = Objects.requireNonNull(decoder);
        this.receipts = Objects.requireNonNull(receipts);
    }

    @RabbitListener(id = "reportCashReceipts", queues = "report.cash-receipts-v2.q",
            containerFactory = "reportCashReceiptListenerFactory")
    public void consume(Message message) {
        try {
            receipts.apply(decoder.decode(message.getMessageProperties().getReceivedRoutingKey(), message.getBody()));
        } catch (ReportEventValidationException | IllegalArgumentException | DateTimeException
                | BusinessRuleException | DuplicateResourceException exception) {
            // A cause may contain raw financial/patient values; do not attach it to broker logs.
            throw new AmqpRejectAndDontRequeueException("Invalid or conflicting cash receipt contract");
        }
        // Storage failures propagate for bounded retries; no successful claim survives rollback.
    }
}
