package com.mediflow.billing.infrastructure.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.mediflow.billing.application.event.InvoiceCreatedEvent;
import com.mediflow.billing.application.event.PaymentCompletedEvent;
import com.mediflow.billing.application.event.PaymentFailedEvent;
import com.mediflow.billing.application.port.out.BillingEventPublisherPort;
import com.mediflow.billing.infrastructure.config.RabbitConfig;

/**
 * Hiện thực {@link BillingEventPublisherPort} — publish domain event của billing lên
 * {@value RabbitConfig#EXCHANGE} (backend-spec/06-billing.md §9, §11).
 *
 * <p><b>Publish sau khi transaction commit</b> (docs/ai/06-events-rabbitmq.md,
 * docs/ai/reference/EventPublisherAdapter.java): các application service (BillingApplicationService,
 * FeeAccrualService, SagaCompensationService) chạy trong {@code @Transactional}; gửi tin nhắn ngay
 * bên trong transaction có thể khiến pharmacy xuất thuốc cho một {@code payment.completed} mà giao
 * dịch DB sau đó rollback. Không dùng outbox ở Phần 5/5 — theo ghi chú trong
 * {@code THELOC-INTEGRATION-FOLLOWUP.md}, outbox cho billing là mốc sau, chưa phải yêu cầu bắt buộc
 * của giai đoạn này.
 */
@Component
public class BillingEventPublisherAdapter implements BillingEventPublisherPort {

    private static final Logger log = LoggerFactory.getLogger(BillingEventPublisherAdapter.class);

    private final RabbitTemplate rabbitTemplate;

    public BillingEventPublisherAdapter(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    @Override
    public void publishInvoiceCreated(InvoiceCreatedEvent e) {
        sendAfterCommit(RabbitConfig.RK_INVOICE_CREATED, e, e.invoiceId());
    }

    @Override
    public void publishPaymentCompleted(PaymentCompletedEvent e) {
        sendAfterCommit(RabbitConfig.RK_PAYMENT_COMPLETED, e, e.invoiceId());
    }

    @Override
    public void publishPaymentFailed(PaymentFailedEvent e) {
        sendAfterCommit(RabbitConfig.RK_PAYMENT_FAILED, e, e.invoiceId());
    }

    private void sendAfterCommit(String routingKey, Object event, java.util.UUID id) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            // Không có transaction đang chạy (test, hoặc gọi trực tiếp) — gửi ngay.
            send(routingKey, event, id);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                send(routingKey, event, id);
            }
        });
    }

    private void send(String routingKey, Object event, java.util.UUID id) {
        rabbitTemplate.convertAndSend(RabbitConfig.EXCHANGE, routingKey, event);
        // id là UUID vô hại, an toàn để log; các field nghiệp vụ (số tiền, lý do...) thì không.
        log.info("Published {} for id={}", routingKey, id);
    }
}
