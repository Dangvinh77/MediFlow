package com.mediflow.clinical.messaging.consumer;

import java.io.IOException;

import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.converter.MessageConversionException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.mediflow.clinical.application.dto.command.ConsumerInboxClaim;
import com.mediflow.clinical.application.exception.ConsumerInboxConflictException;
import com.mediflow.clinical.application.service.ConsumerInboxBoundary;
import com.mediflow.clinical.application.service.OutpatientRegistrationApplicationService;
import com.mediflow.clinical.infrastructure.config.ClinicalOutpatientEligibilityRabbitConfig;
import com.mediflow.clinical.messaging.consumer.inbox.InboxEvidenceFactory;
import com.mediflow.clinical.messaging.consumer.payload.OutpatientFinancialEligibilityEvent;

/** Consumes Billing eligibility only after atomically claiming the raw delivery and payment operation. */
@Component
@ConditionalOnProperty(prefix = "mediflow.clinical.scheduling", name = "enabled", havingValue = "true")
public class ClinicalOutpatientEligibilityConsumer {
    private static final String ROUTING_KEY = "outpatient.financial.eligibility.granted";
    private static final String CONSUMER_NAME = "clinical-outpatient-eligibility";

    private final OutpatientEligibilityEventDecoder decoder;
    private final OutpatientRegistrationApplicationService registrations;
    private final ConsumerInboxBoundary inbox;
    private final InboxEvidenceFactory evidenceFactory;

    public ClinicalOutpatientEligibilityConsumer(OutpatientEligibilityEventDecoder decoder,
            OutpatientRegistrationApplicationService registrations, ConsumerInboxBoundary inbox,
            InboxEvidenceFactory evidenceFactory) {
        this.decoder = decoder;
        this.registrations = registrations;
        this.inbox = inbox;
        this.evidenceFactory = evidenceFactory;
    }

    @RabbitListener(queues = ClinicalOutpatientEligibilityRabbitConfig.ELIGIBILITY_QUEUE,
            containerFactory = "rabbitListenerContainerFactory")
    public void consume(Message message) {
        try {
            OutpatientFinancialEligibilityEvent event = decoder.decode(message.getBody());
            String operationKey = "clinical:outpatient-eligibility:v1:" + event.payload().paymentOperationId()
                    + ":" + event.payload().fenceToken() + ":COMPLETED";
            ConsumerInboxClaim claim = evidenceFactory.create(CONSUMER_NAME, ROUTING_KEY, event.eventId(),
                    operationKey, message.getBody());
            inbox.execute(claim, () -> registrations.consumeEligibility(event.payload()));
        } catch (ConsumerInboxConflictException conflict) {
            throw new AmqpRejectAndDontRequeueException("Clinical eligibility inbox conflict", conflict);
        } catch (IOException | IllegalArgumentException invalid) {
            throw new MessageConversionException("Invalid outpatient.financial.eligibility.granted payload", invalid);
        }
    }
}
