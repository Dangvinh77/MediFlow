package com.mediflow.billing.messaging.consumer;

import com.mediflow.billing.application.port.in.ConsumeOutpatientRegistrationFactUseCase;
import com.mediflow.billing.application.port.out.ScheduledRegistrationWirePort;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

@Component
@ConditionalOnProperty(name = {"mediflow.billing.ledger.enabled", "mediflow.billing.scheduled-payments.enabled"},
        havingValue = "true")
public final class ScheduledRegistrationChangedConsumer {
    private final ScheduledRegistrationWirePort decoder;
    private final ConsumeOutpatientRegistrationFactUseCase facts;

    public ScheduledRegistrationChangedConsumer(ScheduledRegistrationWirePort decoder,
            ConsumeOutpatientRegistrationFactUseCase facts) {
        this.decoder = decoder;
        this.facts = facts;
    }

    @RabbitListener(id = "billingScheduledRegistration", queues = "billing.scheduled-registration.q")
    public void receive(Message message) {
        try {
            var event = decoder.decode(message.getMessageProperties().getReceivedRoutingKey(), message.getBody());
            facts.consume(event, message.getBody());
        } catch (IllegalArgumentException invalid) {
            throw new AmqpRejectAndDontRequeueException("Invalid outpatient registration fact");
        }
    }
}
