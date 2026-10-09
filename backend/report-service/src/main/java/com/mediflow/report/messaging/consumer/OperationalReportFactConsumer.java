package com.mediflow.report.messaging.consumer;

import com.mediflow.common.exception.BusinessRuleException;
import com.mediflow.common.exception.DuplicateResourceException;
import com.mediflow.report.application.port.in.ReceiveOperationalReportFactUseCase;
import com.mediflow.report.application.port.out.CareFinanceWirePort;
import java.util.Objects;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;

/** ACK follows the committed kernel; malformed/conflicting facts retain original bytes in the DLQ. */
public final class OperationalReportFactConsumer {
    private final CareFinanceWirePort decoder;
    private final ReceiveOperationalReportFactUseCase receiver;

    public OperationalReportFactConsumer(CareFinanceWirePort decoder, ReceiveOperationalReportFactUseCase receiver) {
        this.decoder = Objects.requireNonNull(decoder);
        this.receiver = Objects.requireNonNull(receiver);
    }

    @RabbitListener(id = "reportOperationalFacts", queues = "report.operational-v2.q",
            containerFactory = "reportOperationalListenerFactory")
    public void consume(Message message) {
        try {
            receiver.receive(decoder.decode(message.getMessageProperties().getReceivedRoutingKey(), message.getBody()));
        } catch (ReportEventValidationException | IllegalArgumentException | BusinessRuleException
                | DuplicateResourceException exception) {
            // Never retain a cause containing raw producer values or clinical/patient information.
            throw new AmqpRejectAndDontRequeueException("Invalid or conflicting operational report contract");
        }
        // Infrastructure failure propagates for bounded retries, not a false accepted marker.
    }
}
