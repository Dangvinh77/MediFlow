package com.mediflow.billing.infrastructure.messaging;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.mediflow.billing.application.event.InvoiceCreatedEvent;
import com.mediflow.billing.application.event.PaymentCompletedEvent;
import com.mediflow.billing.application.event.PaymentFailedEvent;
import com.mediflow.billing.domain.model.PaymentMethod;
import com.mediflow.billing.infrastructure.config.RabbitConfig;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Xác nhận {@link BillingEventPublisherAdapter} gửi sau commit khi có transaction, và gửi ngay khi
 * không có transaction đang chạy (test/gọi trực tiếp) — quy tắc quan trọng nhất của Phần 5/5 cho
 * saga billing/pharmacy (docs/ai/06-events-rabbitmq.md).
 */
class BillingEventPublisherAdapterTest {

    private final RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
    private final BillingEventPublisherAdapter adapter = new BillingEventPublisherAdapter(rabbitTemplate);

    @AfterEach
    void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void publishInvoiceCreated_withoutActiveTransaction_sendsImmediately() {
        InvoiceCreatedEvent event = new InvoiceCreatedEvent(
                UUID.randomUUID(), Instant.now(), "cid", UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), java.math.BigDecimal.TEN, java.util.List.of());

        adapter.publishInvoiceCreated(event);

        verify(rabbitTemplate).convertAndSend(eq(RabbitConfig.EXCHANGE), eq(RabbitConfig.RK_INVOICE_CREATED), eq(event));
    }

    @Test
    void publishPaymentCompleted_withActiveTransaction_deferredUntilAfterCommit() {
        TransactionSynchronizationManager.initSynchronization();
        try {
            PaymentCompletedEvent event = new PaymentCompletedEvent(
                    UUID.randomUUID(), Instant.now(), "cid", UUID.randomUUID(), UUID.randomUUID(),
                    UUID.randomUUID(), UUID.randomUUID(), java.math.BigDecimal.TEN, PaymentMethod.CASH);

            adapter.publishPaymentCompleted(event);

            // Chưa gửi trong lúc transaction còn mở — publish trước commit có thể kích hoạt
            // pharmacy xuất thuốc cho một khoản thanh toán sau đó bị rollback.
            verify(rabbitTemplate, never()).convertAndSend(
                    eq(RabbitConfig.EXCHANGE), eq(RabbitConfig.RK_PAYMENT_COMPLETED), eq(event));

            for (var synchronization : TransactionSynchronizationManager.getSynchronizations()) {
                synchronization.afterCommit();
            }

            verify(rabbitTemplate).convertAndSend(
                    eq(RabbitConfig.EXCHANGE), eq(RabbitConfig.RK_PAYMENT_COMPLETED), eq(event));
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void publishPaymentFailed_withoutActiveTransaction_sendsImmediately() {
        PaymentFailedEvent event = new PaymentFailedEvent(
                UUID.randomUUID(), Instant.now(), "cid", UUID.randomUUID(), UUID.randomUUID(), "Hết thuốc");

        adapter.publishPaymentFailed(event);

        verify(rabbitTemplate).convertAndSend(eq(RabbitConfig.EXCHANGE), eq(RabbitConfig.RK_PAYMENT_FAILED), eq(event));
    }
}
