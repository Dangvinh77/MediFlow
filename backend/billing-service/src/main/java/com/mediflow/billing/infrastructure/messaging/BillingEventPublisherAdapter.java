package com.mediflow.billing.infrastructure.messaging;

import java.util.UUID;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.billing.application.event.InvoiceCreatedEvent;
import com.mediflow.billing.application.event.PaymentCompletedEvent;
import com.mediflow.billing.application.event.PaymentFailedEvent;
import com.mediflow.billing.application.port.out.BillingEventPublisherPort;
import com.mediflow.billing.infrastructure.config.RabbitConfig;
import com.mediflow.billing.infrastructure.persistence.jpaEntity.BillingEventOutboxJpaEntity;
import com.mediflow.billing.infrastructure.persistence.repository.BillingEventOutboxJpaRepository;

/**
 * Persists Billing integration events to the transactional outbox. The dispatcher publishes only
 * after the surrounding business transaction commits, closing the DB-commit/RabbitMQ crash gap.
 */
@Component
public class BillingEventPublisherAdapter implements BillingEventPublisherPort {

    private final BillingEventOutboxJpaRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public BillingEventPublisherAdapter(BillingEventOutboxJpaRepository outboxRepository,
                                        ObjectMapper objectMapper) {
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    public void publishInvoiceCreated(InvoiceCreatedEvent event) {
        enqueue(RabbitConfig.RK_INVOICE_CREATED, event.eventId(), event.invoiceId(), event);
    }

    @Override
    public void publishPaymentCompleted(PaymentCompletedEvent event) {
        enqueue(RabbitConfig.RK_PAYMENT_COMPLETED, event.eventId(), event.invoiceId(), event);
    }

    @Override
    public void publishPaymentFailed(PaymentFailedEvent event) {
        enqueue(RabbitConfig.RK_PAYMENT_FAILED, event.eventId(), event.invoiceId(), event);
    }

    private void enqueue(String routingKey, UUID eventId, UUID aggregateId, Object payload) {
        try {
            outboxRepository.save(new BillingEventOutboxJpaEntity(
                    eventId, routingKey, aggregateId, objectMapper.writeValueAsString(payload)));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Không thể serialize billing event", exception);
        }
    }
}
