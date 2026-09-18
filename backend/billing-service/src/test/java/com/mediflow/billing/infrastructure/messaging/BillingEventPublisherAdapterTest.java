package com.mediflow.billing.infrastructure.messaging;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import com.mediflow.billing.application.event.InvoiceCreatedEvent;
import com.mediflow.billing.application.event.PaymentCompletedEvent;
import com.mediflow.billing.application.event.PaymentFailedEvent;
import com.mediflow.billing.domain.model.PaymentMethod;
import com.mediflow.billing.infrastructure.persistence.jpaEntity.BillingEventOutboxJpaEntity;
import com.mediflow.billing.infrastructure.persistence.repository.BillingEventOutboxJpaRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import org.mockito.ArgumentCaptor;

/**
 * Xác nhận publisher ghi payload bền vững vào outbox thay vì gửi RabbitMQ trực tiếp.
 */
class BillingEventPublisherAdapterTest {

    private final BillingEventOutboxJpaRepository outboxRepository =
            mock(BillingEventOutboxJpaRepository.class);
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private final BillingEventPublisherAdapter adapter =
            new BillingEventPublisherAdapter(outboxRepository, objectMapper);

    @Test
    void publishInvoiceCreated_writesOutboxWithStableIdentity() {
        InvoiceCreatedEvent event = new InvoiceCreatedEvent(
                UUID.randomUUID(), Instant.now(), "cid", UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), java.math.BigDecimal.TEN, java.util.List.of());

        adapter.publishInvoiceCreated(event);

        BillingEventOutboxJpaEntity row = capturedRow();
        assertThat(row.getEventId()).isEqualTo(event.eventId());
        assertThat(row.getAggregateId()).isEqualTo(event.invoiceId());
        assertThat(row.getRoutingKey()).isEqualTo("invoice.created");
        assertThat(row.getPayload()).contains(event.invoiceId().toString());
    }

    @Test
    void publishPaymentCompleted_writesExactWirePayload() throws Exception {
        PaymentCompletedEvent event = new PaymentCompletedEvent(
                UUID.randomUUID(), Instant.now(), "cid", UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), java.math.BigDecimal.TEN, PaymentMethod.CASH,
                java.util.List.of());

        adapter.publishPaymentCompleted(event);

        BillingEventOutboxJpaEntity row = capturedRow();
        assertThat(row.getRoutingKey()).isEqualTo("payment.completed");
        assertThat(objectMapper.readTree(row.getPayload()).get("prescriptionId").asText())
                .isEqualTo(event.prescriptionId().toString());
        assertThat(objectMapper.readTree(row.getPayload()).get("paymentMethod").asText())
                .isEqualTo("CASH");
    }

    @Test
    void publishPaymentCompleted_writesEmptyLabTestIdsForNonLabInvoice() throws Exception {
        PaymentCompletedEvent event = new PaymentCompletedEvent(
                UUID.randomUUID(), Instant.now(), "cid", UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), java.math.BigDecimal.TEN, PaymentMethod.CASH,
                java.util.List.of());

        adapter.publishPaymentCompleted(event);

        assertThat(capturedRow().getPayload()).contains("\"labTestIds\":[]");
    }

    @Test
    void publishPaymentCompleted_writesSingleLabTestId() throws Exception {
        UUID labTestId = UUID.randomUUID();
        PaymentCompletedEvent event = new PaymentCompletedEvent(
                UUID.randomUUID(), Instant.now(), "cid", UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), null, java.math.BigDecimal.TEN, PaymentMethod.CASH,
                java.util.List.of(labTestId));

        adapter.publishPaymentCompleted(event);

        var labTestIds = objectMapper.readTree(capturedRow().getPayload()).get("labTestIds");
        assertThat(labTestIds).hasSize(1);
        assertThat(labTestIds.get(0).asText()).isEqualTo(labTestId.toString());
    }

    @Test
    void publishPaymentCompleted_writesMultipleDeduplicatedLabTestIds() throws Exception {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        PaymentCompletedEvent event = new PaymentCompletedEvent(
                UUID.randomUUID(), Instant.now(), "cid", UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), null, java.math.BigDecimal.TEN, PaymentMethod.CASH,
                java.util.List.of(first, second));

        adapter.publishPaymentCompleted(event);

        var labTestIds = objectMapper.readTree(capturedRow().getPayload()).get("labTestIds");
        assertThat(labTestIds).hasSize(2);
    }

    @Test
    void publishPaymentFailed_writesCompensationToOutbox() {
        PaymentFailedEvent event = new PaymentFailedEvent(
                UUID.randomUUID(), Instant.now(), "cid", UUID.randomUUID(), UUID.randomUUID(), "Hết thuốc");

        adapter.publishPaymentFailed(event);

        assertThat(capturedRow().getRoutingKey()).isEqualTo("payment.failed");
    }

    private BillingEventOutboxJpaEntity capturedRow() {
        ArgumentCaptor<BillingEventOutboxJpaEntity> captor =
                ArgumentCaptor.forClass(BillingEventOutboxJpaEntity.class);
        verify(outboxRepository).save(captor.capture());
        return captor.getValue();
    }
}
