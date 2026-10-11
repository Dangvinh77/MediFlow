package com.mediflow.report.messaging.consumer;

import com.mediflow.common.exception.BusinessRuleException;
import com.mediflow.report.application.port.in.ApplySettlementSnapshotUseCase;
import com.mediflow.report.application.port.out.CareFinanceWirePort;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;

public final class SettlementSnapshotConsumer {
    private final CareFinanceWirePort decoder;
    private final ApplySettlementSnapshotUseCase snapshots;
    public SettlementSnapshotConsumer(CareFinanceWirePort decoder, ApplySettlementSnapshotUseCase snapshots) {
        this.decoder = decoder; this.snapshots = snapshots;
    }
    @RabbitListener(id = "reportSettlementSnapshots", queues = "report.settlement-snapshots-v2.q",
            containerFactory = "reportSettlementListenerFactory")
    public void consume(Message message) {
        try { snapshots.apply(decoder.decode(message.getMessageProperties().getReceivedRoutingKey(), message.getBody())); }
        catch (ReportEventValidationException | IllegalArgumentException | java.time.DateTimeException | BusinessRuleException error) {
            throw new AmqpRejectAndDontRequeueException("Invalid or conflicting settlement reconciliation contract");
        }
    }
}
