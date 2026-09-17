package com.mediflow.report.messaging.consumer;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.mediflow.report.application.port.in.UpdateAggregateUseCase;
import com.mediflow.report.infrastructure.config.RabbitConfig;

/** Contract and dispatch tests for the single Rabbit driving adapter (T08). */
class ReportEventConsumerTest {

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private final UpdateAggregateUseCase updater = mock(UpdateAggregateUseCase.class);
    private ReportEventConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new ReportEventConsumer(updater, objectMapper);
    }

    @Test
    void medicalRecord_dispatchesUsingExaminationDateAndDepartment() throws Exception {
        consumer.onMessage(message(RabbitConfig.RK_MEDICAL_RECORD_CREATED,
                fixture("medicalrecord.created.json")));

        verify(updater).onMedicalRecordCreated(
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                java.time.LocalDate.of(2026, 9, 15),
                UUID.fromString("44444444-4444-4444-4444-444444444444"));
    }

    @Test
    void labResult_dispatchesUsingPerformedDate() throws Exception {
        consumer.onMessage(message(RabbitConfig.RK_LAB_RESULT_CREATED,
                fixture("lab.result.created.json")));

        verify(updater).onLabResultCreated(
                UUID.fromString("55555555-5555-5555-5555-555555555555"),
                java.time.LocalDate.of(2026, 9, 15),
                UUID.fromString("99999999-9999-9999-9999-999999999999"));
    }

    @Test
    void prescriptionFilled_mapsAllItemsAndIgnoresAdditiveFields() throws Exception {
        consumer.onMessage(message(RabbitConfig.RK_PRESCRIPTION_FILLED,
                fixture("prescription.filled.json")));

        verify(updater).onPrescriptionFilled(
                eq(UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa")),
                eq(Instant.parse("2026-09-16T03:00:00Z")),
                eq(UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd")),
                eq(UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb")),
                any());
    }

    @Test
    void paymentCompleted_dispatchesAmountAndDepartment() throws Exception {
        consumer.onMessage(message(RabbitConfig.RK_PAYMENT_COMPLETED,
                fixture("payment.completed.json")));

        verify(updater).onPaymentCompleted(
                UUID.fromString("12121212-1212-1212-1212-121212121212"),
                Instant.parse("2026-09-16T04:00:00Z"),
                UUID.fromString("13131313-1313-1313-1313-131313131313"),
                UUID.fromString("15151515-1515-1515-1515-151515151515"),
                new java.math.BigDecimal("250000.00"));
    }

    @Test
    void paymentFailed_dispatchesOnlyInvoiceAndDoesNotInferContribution() throws Exception {
        consumer.onMessage(message(RabbitConfig.RK_PAYMENT_FAILED,
                fixture("payment.failed.json")));

        verify(updater).onPaymentFailed(
                UUID.fromString("17171717-1717-1717-1717-171717171717"),
                Instant.parse("2026-09-16T05:00:00Z"),
                UUID.fromString("18181818-1818-1818-1818-181818181818"));
    }

    @Test
    void unknownRoutingKey_isRejectedWithoutCallingApplication() {
        Message message = message("staff.department.changed", "{}".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> consumer.onMessage(message))
                .isInstanceOf(ReportEventValidationException.class);
        verifyNoInteractions(updater);
    }

    @Test
    void malformedPayload_isRejectedWithoutClaimOrEffect() {
        Message message = message(RabbitConfig.RK_PAYMENT_COMPLETED,
                "{\"eventId\":\"not-a-uuid\"}".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> consumer.onMessage(message))
                .isInstanceOf(ReportEventValidationException.class);
        verifyNoInteractions(updater);
    }

    @Test
    void missingRequiredField_isRejectedBeforeUseCase() {
        String payload = "{\"eventId\":\"12121212-1212-1212-1212-121212121212\","
                + "\"occurredAt\":\"2026-09-16T04:00:00Z\",\"correlationId\":\"cid\","
                + "\"invoiceId\":\"13131313-1313-1313-1313-131313131313\"}";

        assertThatThrownBy(() -> consumer.onMessage(message(
                RabbitConfig.RK_PAYMENT_COMPLETED, payload.getBytes(StandardCharsets.UTF_8))))
                .isInstanceOf(ReportEventValidationException.class);
        verifyNoInteractions(updater);
    }

    @Test
    void medicalRecord_missingSourceId_isRejectedBeforeUseCase() {
        String payload = "{\"eventId\":\"12121212-1212-1212-1212-121212121212\","
                + "\"occurredAt\":\"2026-09-16T04:00:00Z\",\"correlationId\":\"cid\","
                + "\"departmentId\":\"44444444-4444-4444-4444-444444444444\","
                + "\"examinationDate\":\"2026-09-16\"}";

        assertThatThrownBy(() -> consumer.onMessage(message(
                RabbitConfig.RK_MEDICAL_RECORD_CREATED, payload.getBytes(StandardCharsets.UTF_8))))
                .isInstanceOf(ReportEventValidationException.class)
                .hasMessageContaining("recordId");
        verifyNoInteractions(updater);
    }

    @Test
    void labResult_missingSourceId_isRejectedBeforeUseCase() {
        String payload = "{\"eventId\":\"12121212-1212-1212-1212-121212121212\","
                + "\"occurredAt\":\"2026-09-16T04:00:00Z\",\"correlationId\":\"cid\","
                + "\"departmentId\":\"44444444-4444-4444-4444-444444444444\","
                + "\"performedDate\":\"2026-09-16\"}";

        assertThatThrownBy(() -> consumer.onMessage(message(
                RabbitConfig.RK_LAB_RESULT_CREATED, payload.getBytes(StandardCharsets.UTF_8))))
                .isInstanceOf(ReportEventValidationException.class)
                .hasMessageContaining("labId");
        verifyNoInteractions(updater);
    }

    private Message message(String routingKey, byte[] body) {
        MessageProperties properties = new MessageProperties();
        properties.setReceivedRoutingKey(routingKey);
        return new Message(body, properties);
    }

    private byte[] fixture(String name) throws IOException {
        try (InputStream stream = getClass().getResourceAsStream("/contracts/" + name)) {
            if (stream == null) {
                throw new IOException("Missing fixture " + name);
            }
            return stream.readAllBytes();
        }
    }
}
