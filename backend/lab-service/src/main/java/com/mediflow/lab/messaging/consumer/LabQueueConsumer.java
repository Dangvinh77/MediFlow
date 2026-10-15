package com.mediflow.lab.messaging.consumer;

import java.io.IOException;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.converter.MessageConversionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.lab.application.dto.command.ConsumerInboxClaim;
import com.mediflow.lab.application.exception.ConsumerInboxConflictException;
import com.mediflow.lab.application.port.out.LabOutpatientMvpMode;
import com.mediflow.lab.application.service.ConsumerInboxBoundary;
import com.mediflow.lab.domain.model.CareEpisodeType;
import com.mediflow.lab.domain.model.ClearancePurpose;
import com.mediflow.lab.infrastructure.config.RabbitConfig;
import com.mediflow.lab.messaging.consumer.inbox.InboxEvidenceFactory;
import com.mediflow.lab.messaging.consumer.payload.FinancialClearanceEnvelope;
import com.mediflow.lab.messaging.consumer.payload.FinancialClearancePayload;
import com.mediflow.lab.messaging.consumer.payload.MedicalRecordCreatedEnvelope;
import com.mediflow.lab.messaging.consumer.payload.MedicalRecordCreatedPayload;
import com.mediflow.lab.messaging.consumer.payload.PaymentCompletedPayload;

/** Sole listener for {@code lab.q}; dispatches by the broker routing key. */
@Component
public class LabQueueConsumer {
    private static final String MEDICAL_RECORD_CONSUMER = "lab-medical-record";
    private static final String PAYMENT_CONSUMER = "lab-payment";
    private static final String CLEARANCE_CONSUMER = "lab-test-clearance";

    private final ObjectMapper objectMapper;
    private final MedicalRecordCreatedConsumer medicalRecords;
    private final PaymentCompletedConsumer payments;
    private final FinancialClearanceConsumer clearances;
    private final boolean outpatientMvpEnabled;
    private final ConsumerInboxBoundary inbox;
    private final InboxEvidenceFactory evidenceFactory;

    @Autowired
    public LabQueueConsumer(ObjectMapper objectMapper, MedicalRecordCreatedConsumer medicalRecords,
                            PaymentCompletedConsumer payments, FinancialClearanceConsumer clearances,
                            LabOutpatientMvpMode outpatientMvpMode, ConsumerInboxBoundary inbox,
                            InboxEvidenceFactory evidenceFactory) {
        this.objectMapper = objectMapper;
        this.medicalRecords = medicalRecords;
        this.payments = payments;
        this.clearances = clearances;
        this.outpatientMvpEnabled = outpatientMvpMode.enabled();
        this.inbox = inbox;
        this.evidenceFactory = evidenceFactory;
    }

    @RabbitListener(queues = "${mediflow.lab.rabbit.queue:lab.q}")
    public void consume(Message message) {
        String routingKey = message.getMessageProperties().getReceivedRoutingKey();
        try {
            switch (routingKey) {
                case RabbitConfig.MEDICAL_RECORD_CREATED -> consumeMedicalRecord(message);
                case RabbitConfig.PAYMENT_COMPLETED -> consumePayment(message);
                case RabbitConfig.FINANCIAL_CLEARANCE_GRANTED -> consumeClearance(message);
                default -> throw new MessageConversionException("Unsupported lab routing key: " + routingKey);
            }
        } catch (ConsumerInboxConflictException conflict) {
            throw new AmqpRejectAndDontRequeueException("Lab inbox conflict", conflict);
        } catch (IOException exception) {
            throw new MessageConversionException("Invalid " + routingKey + " payload", exception);
        } catch (IllegalArgumentException exception) {
            throw new MessageConversionException("Invalid " + routingKey + " payload", exception);
        }
    }

    private void consumeMedicalRecord(Message message) throws IOException {
        JsonNode root = objectMapper.readTree(message.getBody());
        MedicalRecordCreatedPayload payload;
        if (root != null && root.has("payload")) {
            MedicalRecordCreatedEnvelope envelope = objectMapper.treeToValue(root,
                    MedicalRecordCreatedEnvelope.class);
            validateMedicalRecordEnvelope(envelope);
            payload = envelope.toProjection();
        } else {
            if (root == null || root.has("sourceVersion") || root.has("version")
                    || root.has("eventType") || root.has("producer")) {
                throw new IllegalArgumentException(
                        "flat medicalrecord.created cannot assert V1 envelope metadata");
            }
            MedicalRecordCreatedPayload legacy = objectMapper.treeToValue(root, MedicalRecordCreatedPayload.class);
            payload = new MedicalRecordCreatedPayload(legacy.eventId(), legacy.occurredAt(),
                    legacy.correlationId(), legacy.recordId(), legacy.patientId(), legacy.doctorId(),
                    legacy.departmentId(), legacy.diagnosis(), legacy.examinationDate());
        }
        MedicalRecordCreatedConsumer.validate(payload);
        String version = payload.sourceVersion() == 1 ? "v1" : "v0";
        ConsumerInboxClaim claim = evidenceFactory.create(MEDICAL_RECORD_CONSUMER,
                RabbitConfig.MEDICAL_RECORD_CREATED, payload.eventId(),
                "lab:medical-record:" + version + ":" + payload.recordId(), message.getBody());
        inbox.execute(claim, () -> medicalRecords.consume(payload));
    }

    private void consumePayment(Message message) throws IOException {
        if (outpatientMvpEnabled) {
            throw new AmqpRejectAndDontRequeueException(
                    "V0 payment.completed is quarantined in outpatient MVP mode");
        }
        JsonNode root = objectMapper.readTree(message.getBody());
        if (root == null || root.has("payload") || root.has("version") || root.has("eventType")) {
            throw new AmqpRejectAndDontRequeueException(
                    "V1 payment.completed is quarantined until Billing publishes an authorized Lab operation");
        }
        PaymentCompletedPayload payload = objectMapper.treeToValue(root, PaymentCompletedPayload.class);
        PaymentCompletedConsumer.validate(payload);
        ConsumerInboxClaim claim = evidenceFactory.create(PAYMENT_CONSUMER,
                RabbitConfig.PAYMENT_COMPLETED, payload.eventId(),
                "lab:payment:v0:" + payload.invoiceId(), message.getBody(), payload.labTestIds());
        inbox.execute(claim, () -> payments.consume(payload));
    }

    private void consumeClearance(Message message) throws IOException {
        FinancialClearanceEnvelope event = objectMapper.readValue(message.getBody(),
                FinancialClearanceEnvelope.class);
        FinancialClearancePayload payload = FinancialClearanceConsumer.validate(event);
        if (outpatientMvpEnabled && (payload.purpose() == ClearancePurpose.ADMISSION_DEPOSIT
                || payload.purpose() == ClearancePurpose.SURGERY
                || payload.careEpisodeType() == CareEpisodeType.ADMISSION)) {
            throw new AmqpRejectAndDontRequeueException(
                    "Deferred admission/surgery financial evidence is quarantined in outpatient MVP mode");
        }
        String clearanceId = payload.clearanceId().toString();
        if (payload.purpose() == ClearancePurpose.LAB_TEST) {
            FinancialClearanceConsumer.validateLabTarget(payload);
        }
        ConsumerInboxClaim claim = evidenceFactory.create(CLEARANCE_CONSUMER,
                RabbitConfig.FINANCIAL_CLEARANCE_GRANTED, event.eventId(),
                "lab:financial-clearance:v1:" + clearanceId, message.getBody(),
                payload.purpose() == ClearancePurpose.LAB_TEST
                        ? payload.labTestIds().stream().sorted().toList() : java.util.List.of());
        inbox.execute(claim, () -> clearances.consume(event));
    }

    private static void validateMedicalRecordEnvelope(MedicalRecordCreatedEnvelope event) {
        if (event == null || event.eventId() == null
                || !RabbitConfig.MEDICAL_RECORD_CREATED.equals(event.eventType()) || event.version() != 1
                || event.occurredAt() == null || event.correlationId() == null || event.correlationId().isBlank()
                || !"clinical-service".equals(event.producer()) || event.payload() == null) {
            throw new IllegalArgumentException("Invalid medicalrecord.created v1 envelope");
        }
        MedicalRecordCreatedEnvelope.Payload payload = event.payload();
        if (payload.recordId() == null || payload.patientId() == null || payload.doctorId() == null
                || payload.departmentId() == null || payload.careEpisodeId() == null
                || payload.appointmentId() == null || payload.examinationDate() == null
                || !"OUTPATIENT_VISIT".equals(payload.careEpisodeType())
                || !"EXAM".equals(payload.sourceType()) || !payload.appointmentId().equals(payload.sourceId())
                || !payload.appointmentId().equals(payload.careEpisodeId())
                || payload.priceCode() == null || payload.priceCode().isBlank()) {
            throw new IllegalArgumentException("Invalid medicalrecord.created v1 outpatient payload");
        }
    }
}
