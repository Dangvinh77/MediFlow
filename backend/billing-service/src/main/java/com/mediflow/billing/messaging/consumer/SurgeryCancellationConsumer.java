package com.mediflow.billing.messaging.consumer;

import com.mediflow.billing.application.port.in.ProcessSurgeryCancellationUseCase;
import com.mediflow.billing.application.port.out.SurgeryCancellationWirePort;
import com.mediflow.common.exception.BusinessRuleException;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.dao.DuplicateKeyException;

/** Helper selected by the existing billing.q dispatcher; deliberately not a second listener. */
public final class SurgeryCancellationConsumer {
    private final SurgeryCancellationWirePort wire;
    private final ProcessSurgeryCancellationUseCase cancellations;
    public SurgeryCancellationConsumer(SurgeryCancellationWirePort wire, ProcessSurgeryCancellationUseCase cancellations) {
        this.wire = wire;
        this.cancellations = cancellations;
    }
    public void receive(Message message) {
        try {
            cancellations.receive(wire.decode(message.getMessageProperties().getReceivedRoutingKey(), message.getBody()));
        } catch (IllegalArgumentException | BusinessRuleException | DuplicateKeyException invalid) {
            throw new AmqpRejectAndDontRequeueException("Invalid Surgery cancellation fact");
        }
    }
}
