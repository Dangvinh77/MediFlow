package com.mediflow.pharmacy.messaging.consumer;

import static org.mockito.Mockito.verify;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.mediflow.pharmacy.application.dto.command.PaymentCompletedCommand;
import com.mediflow.pharmacy.application.port.in.ReactToPaymentUseCase;
import com.mediflow.pharmacy.messaging.consumer.payload.PaymentCompletedEvent;

/**
 * Kiểm tra driving adapter chỉ chuyển payload RabbitMQ thành application command.
 */
@ExtendWith(MockitoExtension.class)
class PaymentCompletedConsumerTest {

    @Mock
    private ReactToPaymentUseCase reactToPaymentUseCase;

    @InjectMocks
    private PaymentCompletedConsumer consumer;

    /**
     * Consumer phải giữ nguyên toàn bộ envelope và dữ liệu nghiệp vụ khi chuyển tầng.
     */
    @Test
    void consume_validEvent_mapsAllFieldsAndCallsUseCase() {
        UUID eventId = UUID.randomUUID();
        Instant occurredAt = Instant.parse("2026-08-31T03:00:00Z");
        String correlationId = "payment-flow-001";
        UUID invoiceId = UUID.randomUUID();
        UUID patientId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        UUID prescriptionId = UUID.randomUUID();
        BigDecimal totalAmount = new BigDecimal("125000.00");

        PaymentCompletedEvent event = new PaymentCompletedEvent(
                eventId,
                occurredAt,
                correlationId,
                invoiceId,
                patientId,
                departmentId,
                prescriptionId,
                totalAmount,
                "CASH"
        );

        consumer.consume(event);

        verify(reactToPaymentUseCase).onPaymentCompleted(new PaymentCompletedCommand(
                eventId,
                occurredAt,
                correlationId,
                invoiceId,
                patientId,
                departmentId,
                prescriptionId,
                totalAmount,
                "CASH"
        ));
    }
    @Test
    void consume_ordinaryInvoice_doesNotStartDispensing() {
        consumer.consume(new PaymentCompletedEvent(UUID.randomUUID(), Instant.now(), "corr",
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), null, BigDecimal.TEN, "CASH"));
        org.mockito.Mockito.verifyNoInteractions(reactToPaymentUseCase);
    }

    /** JSON contract giữ đúng UUID, amount, correlation và payment method của Billing. */
    @Test
    void payload_jsonContract_deserializesRequiredFields() throws Exception {
        UUID eventId = UUID.randomUUID();
        UUID invoiceId = UUID.randomUUID();
        UUID patientId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        UUID prescriptionId = UUID.randomUUID();
        String json = "{"
                + "\"eventId\":\"" + eventId + "\","
                + "\"occurredAt\":\"2026-08-31T03:00:00Z\","
                + "\"correlationId\":\"payment-flow-001\","
                + "\"invoiceId\":\"" + invoiceId + "\","
                + "\"patientId\":\"" + patientId + "\","
                + "\"departmentId\":\"" + departmentId + "\","
                + "\"prescriptionId\":\"" + prescriptionId + "\","
                + "\"totalAmount\":125000.00,\"paymentMethod\":\"CASH\"}";

        PaymentCompletedEvent event = new ObjectMapper().findAndRegisterModules()
                .readValue(json, PaymentCompletedEvent.class);

        assertThat(event.eventId()).isEqualTo(eventId);
        assertThat(event.invoiceId()).isEqualTo(invoiceId);
        assertThat(event.totalAmount()).isEqualByComparingTo("125000.00");
        assertThat(event.correlationId()).isEqualTo("payment-flow-001");
    }

}
