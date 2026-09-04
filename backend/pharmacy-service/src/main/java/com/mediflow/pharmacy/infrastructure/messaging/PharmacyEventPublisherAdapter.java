package com.mediflow.pharmacy.infrastructure.messaging;

import com.mediflow.pharmacy.application.event.PrescriptionCancelledEvent;
import com.mediflow.pharmacy.application.event.PrescriptionCreatedEvent;
import com.mediflow.pharmacy.application.event.PrescriptionDispenseFailedEvent;
import com.mediflow.pharmacy.application.event.PrescriptionExpiredEvent;
import com.mediflow.pharmacy.application.event.PrescriptionFilledEvent;
import com.mediflow.pharmacy.application.event.StockLowEvent;
import com.mediflow.pharmacy.application.port.out.PharmacyEventPublisherPort;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Adapter phát các sự kiện nghiệp vụ của pharmacy-service.
 *
 * <p>Khi đang có transaction, payload chỉ được gửi sau commit thành công.
 * Nếu transaction rollback, callback afterCommit không được thực thi và bên
 * ngoài sẽ không nhìn thấy sự kiện cho dữ liệu không tồn tại.</p>
 */
@Component
@RequiredArgsConstructor
public class PharmacyEventPublisherAdapter implements PharmacyEventPublisherPort {

    private static final String EXCHANGE = "mediflow.events";

    private static final String PRESCRIPTION_CANCELLED =
            "prescription.cancelled";

    private static final String PRESCRIPTION_CREATED =
            "prescription.created";

    private static final String PRESCRIPTION_FILLED =
            "prescription.filled";

    private static final String PRESCRIPTION_DISPENSE_FAILED =
            "prescription.dispense.failed";

    private static final String PRESCRIPTION_EXPIRED =
            "prescription.expired";

    private static final String STOCK_LOW =
            "stock.low";

    private final RabbitTemplate rabbitTemplate;

    @Override
    public void publishPrescriptionCancelled(
            PrescriptionCancelledEvent event) {

        publishAfterCommit(PRESCRIPTION_CANCELLED, event);
    }

    @Override
    public void publishPrescriptionCreated(
            PrescriptionCreatedEvent event) {

        publishAfterCommit(PRESCRIPTION_CREATED, event);
    }

    @Override
    public void publishPrescriptionFilled(
            PrescriptionFilledEvent event) {

        publishAfterCommit(PRESCRIPTION_FILLED, event);
    }

    @Override
    public void publishPrescriptionDispenseFailed(
            PrescriptionDispenseFailedEvent event) {

        publishAfterCommit(PRESCRIPTION_DISPENSE_FAILED, event);
    }

    @Override
    public void publishPrescriptionExpired(
            PrescriptionExpiredEvent event) {

        publishAfterCommit(PRESCRIPTION_EXPIRED, event);
    }

    @Override
    public void publishStockLow(StockLowEvent event) {
        publishAfterCommit(STOCK_LOW, event);
    }

    /**
     * Đăng ký gửi payload sau commit nếu đang ở trong transaction.
     *
     * <p>Khi adapter được gọi ngoài transaction, chẳng hạn từ unit test hoặc
     * một tác vụ độc lập, payload được gửi ngay.</p>
     */
    private void publishAfterCommit(
            String routingKey,
            Object payload) {

        if (!TransactionSynchronizationManager
                .isSynchronizationActive()) {

            publishNow(routingKey, payload);
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        publishNow(routingKey, payload);
                    }
                });
    }

    private void publishNow(
            String routingKey,
            Object payload) {

        rabbitTemplate.convertAndSend(
                EXCHANGE,
                routingKey,
                payload);
    }
}
