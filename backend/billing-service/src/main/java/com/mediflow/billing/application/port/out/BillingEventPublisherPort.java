package com.mediflow.billing.application.port.out;

import com.mediflow.billing.application.event.InvoiceCreatedEvent;
import com.mediflow.billing.application.event.PaymentCompletedEvent;
import com.mediflow.billing.application.event.PaymentFailedEvent;

/**
 * Out-port — "tôi cần ai đó biết cách báo tin cho các service khác".
 * Application phải công bố các sự kiện nghiệp vụ nhưng <b>không</b> được đụng RabbitMQ.
 * Adapter thật là {@code BillingEventPublisherAdapter} trong {@code infrastructure/messaging}
 * (Phần 5/5), publish <b>sau khi transaction commit</b> (backend-spec/06-billing.md §11,
 * docs/ai/06-events-rabbitmq.md).
 */
public interface BillingEventPublisherPort {

    /** Publish {@code invoice.created} sau khi lập hóa đơn commit. */
    void publishInvoiceCreated(InvoiceCreatedEvent e);

    /** Publish {@code payment.completed} sau khi thanh toán commit — kích hoạt pharmacy xuất thuốc. */
    void publishPaymentCompleted(PaymentCompletedEvent e);

    /** Publish {@code payment.failed} trong nhánh bù trừ saga — notification báo bệnh nhân (BR-B5). */
    void publishPaymentFailed(PaymentFailedEvent e);
}
