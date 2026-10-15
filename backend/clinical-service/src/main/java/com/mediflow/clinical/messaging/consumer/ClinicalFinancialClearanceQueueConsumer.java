package com.mediflow.clinical.messaging.consumer;

import java.io.IOException;

import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.converter.MessageConversionException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.clinical.application.dto.command.ConsumerInboxClaim;
import com.mediflow.clinical.application.exception.ConsumerInboxConflictException;
import com.mediflow.clinical.application.service.ConsumerInboxBoundary;
import com.mediflow.clinical.application.port.out.OutpatientMvpMode;
import com.mediflow.clinical.domain.model.CareEpisodeType;
import com.mediflow.clinical.domain.model.ClearancePurpose;
import com.mediflow.clinical.infrastructure.config.ClinicalClearanceRabbitConfig;
import com.mediflow.clinical.messaging.consumer.inbox.InboxEvidenceFactory;
import com.mediflow.clinical.messaging.consumer.payload.FinancialClearanceEvent;

@Component
@ConditionalOnProperty(prefix = "mediflow.features.care-finance-v2", name = "enabled", havingValue = "true")
public class ClinicalFinancialClearanceQueueConsumer {
    private final ObjectMapper objectMapper;
    private final FinancialClearanceConsumer consumer;
    private final ConsumerInboxBoundary inbox;
    private final InboxEvidenceFactory evidenceFactory;
    private final boolean outpatientMvpEnabled;

    public ClinicalFinancialClearanceQueueConsumer(ObjectMapper objectMapper,
                                                   FinancialClearanceConsumer consumer,
                                                   ConsumerInboxBoundary inbox,
                                                   InboxEvidenceFactory evidenceFactory,
                                                   OutpatientMvpMode outpatientMvpMode) {
        this.objectMapper = objectMapper;
        this.consumer = consumer;
        this.inbox = inbox;
        this.evidenceFactory = evidenceFactory;
        this.outpatientMvpEnabled = outpatientMvpMode.enabled();
    }

    @RabbitListener(queues = ClinicalClearanceRabbitConfig.CLEARANCE_QUEUE, containerFactory = "clinicalClearanceListenerContainerFactory")
    public void consume(Message message) {
        try {
            FinancialClearanceEvent event = objectMapper.readValue(message.getBody(), FinancialClearanceEvent.class);
            var payload = FinancialClearanceConsumer.validate(event);
            ClearancePurpose purpose = ClearancePurpose.valueOf(payload.purpose());
            CareEpisodeType episodeType = CareEpisodeType.valueOf(payload.careEpisodeType());
            if (outpatientMvpEnabled && (purpose == ClearancePurpose.ADMISSION_DEPOSIT
                    || purpose == ClearancePurpose.SURGERY || episodeType == CareEpisodeType.ADMISSION)) {
                throw new AmqpRejectAndDontRequeueException(
                        "Deferred admission/surgery financial evidence is quarantined in outpatient MVP mode");
            }
            String clearanceId = payload.clearanceId().toString();
            String key = "clinical:clearance:v1:" + clearanceId;
            ConsumerInboxClaim claim = evidenceFactory.create("clinical-exam-clearance",
                    ClinicalClearanceRabbitConfig.CLEARANCE_ROUTING_KEY, event.eventId(), key,
                    message.getBody());
            inbox.execute(claim, () -> consumer.consume(event));
        } catch (ConsumerInboxConflictException conflict) {
            throw new AmqpRejectAndDontRequeueException("Clinical clearance inbox conflict", conflict);
        } catch (IOException exception) {
            throw new MessageConversionException("Invalid financial.clearance.granted payload", exception);
        } catch (IllegalArgumentException exception) {
            throw new MessageConversionException("Invalid financial.clearance.granted payload", exception);
        }
    }
}
