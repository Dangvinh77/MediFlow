package com.mediflow.report.messaging.consumer;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.report.application.dto.command.DispensedItem;
import com.mediflow.report.application.port.in.UpdateAggregateUseCase;
import com.mediflow.report.infrastructure.config.RabbitConfig;
import com.mediflow.report.messaging.consumer.payload.LabResultCreatedPayload;
import com.mediflow.report.messaging.consumer.payload.MedicalRecordCreatedPayload;
import com.mediflow.report.messaging.consumer.payload.PaymentCompletedPayload;
import com.mediflow.report.messaging.consumer.payload.PaymentFailedPayload;
import com.mediflow.report.messaging.consumer.payload.PrescriptionFilledPayload;

/** Single driving adapter that dispatches the five report event contracts. */
@Component
public class ReportEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(ReportEventConsumer.class);

    private final UpdateAggregateUseCase updateAggregateUseCase;
    private final ObjectMapper objectMapper;

    public ReportEventConsumer(UpdateAggregateUseCase updateAggregateUseCase,
                               ObjectMapper objectMapper) {
        this.updateAggregateUseCase = updateAggregateUseCase;
        this.objectMapper = objectMapper.copy()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    /**
     * Parse and validate one delivery, then call exactly one application use-case method.
     * Exceptions deliberately escape so the listener retry interceptor can route poison messages
     * to the DLQ. Idempotency and all projection effects remain in the application transaction.
     */
    @RabbitListener(queues = RabbitConfig.QUEUE)
    public void onMessage(Message message) {
        if (message == null || message.getMessageProperties() == null) {
            throw invalid("message không hợp lệ");
        }
        String routingKey = message.getMessageProperties().getReceivedRoutingKey();
        if (routingKey == null || routingKey.isBlank()) {
            throw invalid("routing key là bắt buộc");
        }

        switch (routingKey) {
            case RabbitConfig.RK_MEDICAL_RECORD_CREATED ->
                    handleMedical(read(message, MedicalRecordCreatedPayload.class), routingKey);
            case RabbitConfig.RK_LAB_RESULT_CREATED ->
                    handleLab(read(message, LabResultCreatedPayload.class), routingKey);
            case RabbitConfig.RK_PRESCRIPTION_FILLED ->
                    handlePrescription(read(message, PrescriptionFilledPayload.class), routingKey);
            case RabbitConfig.RK_PAYMENT_COMPLETED ->
                    handlePaymentCompleted(read(message, PaymentCompletedPayload.class), routingKey);
            case RabbitConfig.RK_PAYMENT_FAILED ->
                    handlePaymentFailed(read(message, PaymentFailedPayload.class), routingKey);
            default -> throw invalid("routing key không được hỗ trợ: " + routingKey);
        }
    }

    private void handleMedical(MedicalRecordCreatedPayload event, String routingKey) {
        validateEnvelope(event.eventId(), event.occurredAt(), event.correlationId(), routingKey);
        require(event.recordId(), "recordId");
        require(event.examinationDate(), "examinationDate");
        require(event.departmentId(), "departmentId");
        log.info("report event accepted routingKey={} eventId={} correlationId={} sourceId={}",
                routingKey, event.eventId(), event.correlationId(), event.recordId());
        updateAggregateUseCase.onMedicalRecordCreated(event.eventId(), event.examinationDate(),
                event.departmentId());
    }

    private void handleLab(LabResultCreatedPayload event, String routingKey) {
        validateEnvelope(event.eventId(), event.occurredAt(), event.correlationId(), routingKey);
        require(event.labId(), "labId");
        require(event.performedDate(), "performedDate");
        require(event.departmentId(), "departmentId");
        log.info("report event accepted routingKey={} eventId={} correlationId={} sourceId={}",
                routingKey, event.eventId(), event.correlationId(), event.labId());
        updateAggregateUseCase.onLabResultCreated(event.eventId(), event.performedDate(),
                event.departmentId());
    }

    private void handlePrescription(PrescriptionFilledPayload event, String routingKey) {
        validateEnvelope(event.eventId(), event.occurredAt(), event.correlationId(), routingKey);
        require(event.prescriptionId(), "prescriptionId");
        require(event.departmentId(), "departmentId");
        if (event.dispensedItems() == null || event.dispensedItems().isEmpty()
                || event.dispensedItems().stream().anyMatch(item -> item == null)) {
            throw invalid("dispensedItems không được rỗng");
        }
        List<DispensedItem> items;
        try {
            items = event.dispensedItems().stream()
                    .map(item -> new DispensedItem(item.drugId(), item.drugName(), item.quantity()))
                    .toList();
        } catch (RuntimeException exception) {
            if (exception instanceof ReportEventValidationException validationException) {
                throw validationException;
            }
            throw invalid("dispensedItems chứa dữ liệu không hợp lệ");
        }
        log.info("report event accepted routingKey={} eventId={} correlationId={} sourceId={}",
                routingKey, event.eventId(), event.correlationId(), event.prescriptionId());
        updateAggregateUseCase.onPrescriptionFilled(event.eventId(), event.occurredAt(),
                event.departmentId(), event.prescriptionId(), items);
    }

    private void handlePaymentCompleted(PaymentCompletedPayload event, String routingKey) {
        validateEnvelope(event.eventId(), event.occurredAt(), event.correlationId(), routingKey);
        require(event.invoiceId(), "invoiceId");
        require(event.totalAmount(), "totalAmount");
        if (event.totalAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw invalid("totalAmount phải lớn hơn 0");
        }
        log.info("report event accepted routingKey={} eventId={} correlationId={} sourceId={}",
                routingKey, event.eventId(), event.correlationId(), event.invoiceId());
        updateAggregateUseCase.onPaymentCompleted(event.eventId(), event.occurredAt(),
                event.invoiceId(), event.departmentId(), event.totalAmount());
    }

    private void handlePaymentFailed(PaymentFailedPayload event, String routingKey) {
        validateEnvelope(event.eventId(), event.occurredAt(), event.correlationId(), routingKey);
        require(event.invoiceId(), "invoiceId");
        // payment.failed intentionally has no amount/department; reversal uses the contribution row.
        log.info("report event accepted routingKey={} eventId={} correlationId={} sourceId={}",
                routingKey, event.eventId(), event.correlationId(), event.invoiceId());
        updateAggregateUseCase.onPaymentFailed(event.eventId(), event.occurredAt(), event.invoiceId());
    }

    private <T> T read(Message message, Class<T> type) {
        try {
            T parsed = objectMapper.readValue(message.getBody(), type);
            if (parsed == null) {
                throw invalid("payload event không được null");
            }
            return parsed;
        } catch (IOException | RuntimeException exception) {
            if (exception instanceof ReportEventValidationException validationException) {
                throw validationException;
            }
            throw new ReportEventValidationException("Payload event không hợp lệ", exception);
        }
    }

    private static void validateEnvelope(UUID eventId, Instant occurredAt, String correlationId,
                                         String routingKey) {
        require(eventId, "eventId");
        require(occurredAt, "occurredAt");
        if (correlationId == null || correlationId.isBlank()) {
            throw invalid("correlationId là bắt buộc cho " + routingKey);
        }
    }

    private static void require(Object value, String field) {
        if (value == null || (value instanceof String text && text.isBlank())) {
            throw invalid(field + " là bắt buộc");
        }
    }

    private static ReportEventValidationException invalid(String message) {
        return new ReportEventValidationException(message);
    }
}
