package com.mediflow.pharmacy.application.service;

import com.mediflow.pharmacy.application.dto.command.PaymentCompletedCommand;
import com.mediflow.pharmacy.application.event.PrescriptionDispenseFailedEvent;
import com.mediflow.pharmacy.application.port.out.PharmacyEventPublisherPort;
import com.mediflow.pharmacy.application.port.out.ProcessedEventPort;
import com.mediflow.pharmacy.domain.model.Prescription;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Atomically claims a late payment event and records its compensation event.
 *
 * <p>The processed-event row and transactional-outbox row share one database transaction. A
 * concurrent redelivery therefore cannot publish two compensation events, while a rollback leaves
 * the payment event available for RabbitMQ retry.</p>
 */
@Service
public class LatePaymentCompensationService {

    private static final String PAYMENT_COMPLETED_ROUTING_KEY = "payment.completed";

    private final ProcessedEventPort processedEventPort;
    private final PharmacyEventPublisherPort eventPublisher;
    private final Clock clock;

    /** Creates the atomic late-payment handler. */
    public LatePaymentCompensationService(
            ProcessedEventPort processedEventPort,
            PharmacyEventPublisherPort eventPublisher,
            Clock clock) {
        this.processedEventPort = processedEventPort;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    /**
     * Emits compensation once when payment arrives after prescription cancellation or expiry.
     *
     * @param command payment event being handled
     * @param prescription terminal prescription matching the payment context
     * @return {@code true} when this call claimed and recorded compensation; otherwise {@code false}
     */
    @Transactional
    public boolean compensate(PaymentCompletedCommand command, Prescription prescription) {
        if (!processedEventPort.claimIfAbsent(command.eventId(), PAYMENT_COMPLETED_ROUTING_KEY)) {
            return false;
        }
        eventPublisher.publishPrescriptionDispenseFailed(new PrescriptionDispenseFailedEvent(
                UUID.randomUUID(),
                Instant.now(clock),
                command.correlationId(),
                command.prescriptionId(),
                command.invoiceId(),
                prescription.getPatientId(),
                "PAYMENT_AFTER_TERMINAL_STATE",
                List.of()));
        return true;
    }
}
