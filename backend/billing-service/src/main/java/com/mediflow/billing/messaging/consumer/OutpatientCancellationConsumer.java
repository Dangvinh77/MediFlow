package com.mediflow.billing.messaging.consumer;

import com.mediflow.billing.application.port.in.ProcessOutpatientCancellationUseCase;
import com.mediflow.billing.application.port.out.OutpatientCancellationWirePort;
import com.mediflow.common.exception.BusinessRuleException;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.dao.DuplicateKeyException;

/** Strict handler on the shared billing.q dispatcher; it never opens a second competing listener. */
public class OutpatientCancellationConsumer {
    private final OutpatientCancellationWirePort wire;
    private final ProcessOutpatientCancellationUseCase cancellations;
    public OutpatientCancellationConsumer(OutpatientCancellationWirePort wire,
            ProcessOutpatientCancellationUseCase cancellations) {
        this.wire = wire; this.cancellations = cancellations;
    }
    public void receive(Message message) {
        try {
            cancellations.receive(wire.decode(message.getMessageProperties().getReceivedRoutingKey(), message.getBody()));
        } catch (IllegalArgumentException | BusinessRuleException | DuplicateKeyException invalid) {
            throw new AmqpRejectAndDontRequeueException("Invalid or conflicting outpatient cancellation fact");
        }
    }
}
