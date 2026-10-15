package com.mediflow.clinical.messaging.consumer;

import java.io.IOException;

import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.converter.MessageConversionException;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.clinical.application.dto.command.ConsumerInboxClaim;
import com.mediflow.clinical.application.exception.ConsumerInboxConflictException;
import com.mediflow.clinical.application.port.out.OutpatientMvpMode;
import com.mediflow.clinical.application.service.ConsumerInboxBoundary;
import com.mediflow.clinical.infrastructure.config.RabbitConfig;
import com.mediflow.clinical.messaging.consumer.inbox.InboxEvidenceFactory;
import com.mediflow.clinical.messaging.consumer.payload.LabResultCreatedEnvelope;
import com.mediflow.clinical.messaging.consumer.payload.LabResultCreatedPayload;
import com.mediflow.clinical.messaging.consumer.payload.PrescriptionFilledPayload;

/** Sole listener for {@code clinical.q}; dispatches by the broker routing key. */
@Component
public class ClinicalQueueConsumer {
    private static final String LAB_RESULT_CONSUMER = "clinical-lab-result";
    private static final String PRESCRIPTION_CONSUMER = "clinical-prescription";

    private final ObjectMapper objectMapper;
    private final LabResultCreatedConsumer labResults;
    private final PrescriptionFilledConsumer prescriptions;
    private final ConsumerInboxBoundary inbox;
    private final InboxEvidenceFactory evidenceFactory;
    private final boolean outpatientMvpEnabled;

    public ClinicalQueueConsumer(ObjectMapper objectMapper, LabResultCreatedConsumer labResults,
                                 PrescriptionFilledConsumer prescriptions, ConsumerInboxBoundary inbox,
                                 InboxEvidenceFactory evidenceFactory, OutpatientMvpMode outpatientMvpMode) {
        this.objectMapper = objectMapper;
        this.labResults = labResults;
        this.prescriptions = prescriptions;
        this.inbox = inbox;
        this.evidenceFactory = evidenceFactory;
        this.outpatientMvpEnabled = outpatientMvpMode.enabled();
    }

    @RabbitListener(queues = "${mediflow.clinical.rabbit.queue:clinical.q}")
    public void consume(Message message) {
        String routingKey = message.getMessageProperties().getReceivedRoutingKey();
        try {
            switch (routingKey) {
                case RabbitConfig.LAB_RESULT_CREATED -> consumeLabResult(message);
                case RabbitConfig.PRESCRIPTION_FILLED -> consumePrescription(message);
                default -> throw new MessageConversionException(
                        "Unsupported clinical routing key: " + routingKey);
            }
        } catch (ConsumerInboxConflictException conflict) {
            throw new AmqpRejectAndDontRequeueException("Clinical inbox conflict", conflict);
        } catch (IOException exception) {
            throw new MessageConversionException("Invalid " + routingKey + " payload", exception);
        } catch (IllegalArgumentException exception) {
            throw new MessageConversionException("Invalid " + routingKey + " payload", exception);
        }
    }

    private void consumeLabResult(Message message) throws IOException {
        LabResultCreatedPayload payload = decodeLabResult(message);
        LabResultCreatedConsumer.validate(payload);
        if (outpatientMvpEnabled && payload.sourceVersion() == 0) {
            throw new AmqpRejectAndDontRequeueException(
                    "Flat V0 lab.result.created lacks outpatient identity and is quarantined in MVP mode");
        }
        String operationKey = payload.sourceVersion() == 1
                ? "clinical:lab-result:v1:" + payload.labId() + ":" + payload.resultVersion()
                : "clinical:lab-result:v0:" + payload.labId();
        ConsumerInboxClaim claim = evidenceFactory.create(LAB_RESULT_CONSUMER,
                RabbitConfig.LAB_RESULT_CREATED, payload.eventId(), operationKey, message.getBody());
        inbox.execute(claim, () -> labResults.consume(payload));
    }

    private void consumePrescription(Message message) throws IOException {
        var root = objectMapper.readTree(message.getBody());
        if (root == null || root.has("payload") || root.has("version")
                || root.has("eventType") || root.has("producer")) {
            throw new MessageConversionException(
                    "V1 prescription.filled is unsupported by the current Clinical attachment contract");
        }
        PrescriptionFilledPayload payload = objectMapper.treeToValue(root, PrescriptionFilledPayload.class);
        PrescriptionFilledConsumer.validate(payload);
        if (outpatientMvpEnabled) {
            throw new AmqpRejectAndDontRequeueException(
                    "Flat V0 prescription.filled lacks outpatient identity and is quarantined in MVP mode");
        }
        ConsumerInboxClaim claim = evidenceFactory.create(PRESCRIPTION_CONSUMER,
                RabbitConfig.PRESCRIPTION_FILLED, payload.eventId(),
                "clinical:prescription:v0:" + payload.prescriptionId(), message.getBody());
        inbox.execute(claim, () -> prescriptions.consume(payload));
    }

    private LabResultCreatedPayload decodeLabResult(Message message) throws IOException {
        var root = objectMapper.readTree(message.getBody());
        if (root != null && root.has("payload")) {
            LabResultCreatedEnvelope envelope = objectMapper.treeToValue(root, LabResultCreatedEnvelope.class);
            if (envelope == null) {
                throw invalidLabEnvelope("envelope is required");
            }
            if (envelope.version() != 1) {
                throw invalidLabEnvelope("version must be 1");
            }
            if (!RabbitConfig.LAB_RESULT_CREATED.equals(envelope.eventType())) {
                throw invalidLabEnvelope("eventType must be lab.result.created");
            }
            if (!"lab-service".equals(envelope.producer())) {
                throw invalidLabEnvelope("producer must be lab-service");
            }
            if (envelope.eventId() == null || envelope.occurredAt() == null
                    || envelope.correlationId() == null || envelope.correlationId().isBlank()
                    || envelope.payload() == null) {
                throw invalidLabEnvelope("required envelope fields are missing");
            }
            if (envelope.payload().resultVersion() == null || envelope.payload().resultVersion() != 1) {
                throw invalidLabEnvelope("resultVersion must be 1 in the outpatient MVP");
            }
            return envelope.toProjection();
        }
        if (root == null || root.has("sourceVersion") || root.has("version")
                || root.has("eventType") || root.has("producer")) {
            throw invalidLabEnvelope("flat compatibility payload cannot assert V1 envelope metadata");
        }
        LabResultCreatedPayload legacy = objectMapper.treeToValue(root, LabResultCreatedPayload.class);
        return new LabResultCreatedPayload(legacy.eventId(), legacy.occurredAt(), legacy.correlationId(),
                legacy.labId(), legacy.patientId(), legacy.recordId(), legacy.departmentId(),
                legacy.labType(), legacy.performedDate(), legacy.results(), legacy.conclusion());
    }

    private static MessageConversionException invalidLabEnvelope(String reason) {
        return new MessageConversionException("Invalid lab.result.created envelope: " + reason);
    }
}
