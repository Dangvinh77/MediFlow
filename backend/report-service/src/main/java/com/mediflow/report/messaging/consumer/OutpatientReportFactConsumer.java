package com.mediflow.report.messaging.consumer;

import com.mediflow.common.exception.BusinessRuleException;
import com.mediflow.common.exception.DuplicateResourceException;
import com.mediflow.report.application.port.in.ReceiveOutpatientReportFactUseCase;
import com.mediflow.report.application.port.out.CareFinanceWirePort;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;

public class OutpatientReportFactConsumer {
    private final CareFinanceWirePort decoder;
    private final ReceiveOutpatientReportFactUseCase receiver;
    public OutpatientReportFactConsumer(CareFinanceWirePort decoder, ReceiveOutpatientReportFactUseCase receiver) {
        this.decoder = decoder; this.receiver = receiver;
    }
    @RabbitListener(id="reportOutpatientFacts", queues="report.outpatient-v1.q", containerFactory="reportOutpatientListenerFactory")
    public void consume(Message message) {
        try {
            receiver.receive(decoder.decode(message.getMessageProperties().getReceivedRoutingKey(), message.getBody()));
        } catch (ReportEventValidationException | IllegalArgumentException | java.time.DateTimeException
                | BusinessRuleException | DuplicateResourceException invalid) {
            throw new AmqpRejectAndDontRequeueException("Invalid, conflicting or deferred outpatient report contract");
        }
        // Storage/transport failures propagate for bounded retry; there is no financial ACK-drop.
    }
}
