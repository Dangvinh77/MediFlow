package com.mediflow.pharmacy.infrastructure.messaging;

import com.mediflow.pharmacy.application.event.PrescriptionCancelledEvent;
import com.mediflow.pharmacy.application.event.PrescriptionCreatedEvent;
import com.mediflow.pharmacy.application.event.PrescriptionDispenseFailedEvent;
import com.mediflow.pharmacy.application.event.PrescriptionExpiredEvent;
import com.mediflow.pharmacy.application.event.PrescriptionFilledEvent;
import com.mediflow.pharmacy.application.event.StockLowEvent;
import com.mediflow.pharmacy.application.event.StockAdjustedEvent;
import com.mediflow.pharmacy.application.port.out.PharmacyEventPublisherPort;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.pharmacy.infrastructure.persistence.jpaentity.PharmacyEventOutboxJpaEntity;
import com.mediflow.pharmacy.infrastructure.persistence.repository.PharmacyEventOutboxJpaRepository;
import org.springframework.stereotype.Component;
import java.util.UUID;

/**
 * Adapter phát các sự kiện nghiệp vụ của pharmacy-service.
 *
 * <p>Trong production payload được ghi vào transactional outbox cùng transaction nghiệp vụ.
 * Dispatcher sẽ gửi lại các dòng chưa published sau commit; vì vậy crash giữa DB commit và
 * RabbitMQ không làm mất event.</p>
 */
@Component
public class PharmacyEventPublisherAdapter implements PharmacyEventPublisherPort {

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

    private static final String STOCK_ADJUSTED =
            "stock.adjusted";

    private final PharmacyEventOutboxJpaRepository outboxRepository;
    private final ObjectMapper objectMapper;

    /** Creates the adapter that persists events before the surrounding transaction commits. */
    public PharmacyEventPublisherAdapter(
            PharmacyEventOutboxJpaRepository outboxRepository,
            ObjectMapper objectMapper) {
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    public void publishPrescriptionCancelled(
            PrescriptionCancelledEvent event) {

        enqueue(PRESCRIPTION_CANCELLED, event.eventId(), event.prescriptionId(), event);
    }

    @Override
    public void publishPrescriptionCreated(
            PrescriptionCreatedEvent event) {

        enqueue(PRESCRIPTION_CREATED, event.eventId(), event.prescriptionId(), event);
    }

    @Override
    public void publishPrescriptionFilled(
            PrescriptionFilledEvent event) {

        enqueue(PRESCRIPTION_FILLED, event.eventId(), event.prescriptionId(), event);
    }

    @Override
    public void publishPrescriptionDispenseFailed(
            PrescriptionDispenseFailedEvent event) {

        enqueue(PRESCRIPTION_DISPENSE_FAILED, event.eventId(), event.prescriptionId(), event);
    }

    @Override
    public void publishPrescriptionExpired(
            PrescriptionExpiredEvent event) {

        enqueue(PRESCRIPTION_EXPIRED, event.eventId(), event.prescriptionId(), event);
    }

    @Override
    public void publishStockLow(StockLowEvent event) {
        enqueue(STOCK_LOW, event.eventId(), event.drugId(), event);
    }

    /** Records stock-adjustment audit for durable delivery. */
    @Override
    public void publishStockAdjusted(StockAdjustedEvent event) {
        enqueue(STOCK_ADJUSTED, event.eventId(), event.drugId(), event);
    }

    /**
     * Serializes and stores one event in the durable outbox.
     */
    private void enqueue(
            String routingKey,
            UUID eventId,
            UUID aggregateId,
            Object payload) {
        try {
            String json = objectMapper.writeValueAsString(payload);
            outboxRepository.save(new PharmacyEventOutboxJpaEntity(
                    eventId, routingKey, aggregateId, json));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Không thể serialize pharmacy event", exception);
        }
    }
}
