package com.mediflow.report.messaging.consumer;

import com.mediflow.report.application.port.in.ApplyCashRefundUseCase;
import com.mediflow.report.application.port.out.CareFinanceWirePort;
import com.mediflow.common.exception.BusinessRuleException;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;

public class CashRefundConsumer {
    private final CareFinanceWirePort wire;
    private final ApplyCashRefundUseCase refunds;
    public CashRefundConsumer(CareFinanceWirePort wire, ApplyCashRefundUseCase refunds) { this.wire = wire; this.refunds = refunds; }
    @RabbitListener(id = "reportCashRefunds", queues = "report.cash-refunds-v2.q", containerFactory = "reportCashRefundListenerFactory")
    public void receive(Message message) {
        try { refunds.apply(wire.decode(message.getMessageProperties().getReceivedRoutingKey(), message.getBody())); }
        catch (ReportEventValidationException | IllegalArgumentException | BusinessRuleException invalid) {
            throw new AmqpRejectAndDontRequeueException("Invalid or conflicting completed cash refund");
        }
    }
}
