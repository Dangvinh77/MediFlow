package com.mediflow.patient.infrastructure.messaging;

import com.mediflow.patient.application.port.out.PatientEventPublisherPort;
import com.mediflow.patient.domain.model.Patient;
import com.mediflow.patient.infrastructure.config.RabbitConfig;
import com.mediflow.patient.infrastructure.messaging.payload.PatientCreatedEvent;
import com.mediflow.patient.infrastructure.messaging.payload.PatientUpdatedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.UUID;

/**
 * Publishes patient events to the shared topic exchange.
 *
 * <p><strong>Sends after the transaction commits.</strong> The application service calls this
 * inside its {@code @Transactional} method, but the actual send is deferred through a
 * {@link TransactionSynchronization}. Publishing inline would announce a patient that a later
 * rollback erases — and consumers cannot un-see an event.
 *
 * <p>Keeping this here, rather than injecting Spring's event publisher into the application layer,
 * is what lets that layer stay free of Spring beyond {@code @Service}/{@code @Transactional}.
 */
@Component
public class PatientEventPublisherAdapter implements PatientEventPublisherPort {

    private static final Logger log = LoggerFactory.getLogger(PatientEventPublisherAdapter.class);

    private final RabbitTemplate rabbitTemplate;

    public PatientEventPublisherAdapter(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    @Override
    public void publishCreated(Patient patient) {
        PatientCreatedEvent event = new PatientCreatedEvent(
                UUID.randomUUID(), Instant.now(), null,
                patient.getMaBenhNhan(), patient.getHoTen(), patient.getEmail(), patient.getSoDienThoai());

        guiSauKhiCommit(RabbitConfig.RK_PATIENT_CREATED, event, patient.getMaBenhNhan());
    }

    @Override
    public void publishUpdated(Patient patient) {
        PatientUpdatedEvent event = new PatientUpdatedEvent(
                UUID.randomUUID(), Instant.now(), null,
                patient.getMaBenhNhan(), patient.getHoTen(), patient.getEmail(),
                patient.getSoDienThoai(), patient.getDiaChi());

        guiSauKhiCommit(RabbitConfig.RK_PATIENT_UPDATED, event, patient.getMaBenhNhan());
    }

    private void guiSauKhiCommit(String routingKey, Object event, UUID patientId) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            // No transaction in progress (a test, or a direct call) — send immediately.
            gui(routingKey, event, patientId);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                gui(routingKey, event, patientId);
            }
        });
    }

    private void gui(String routingKey, Object event, UUID patientId) {
        rabbitTemplate.convertAndSend(RabbitConfig.EXCHANGE, routingKey, event);
        // patientId is an opaque UUID, safe to log. Name, CMND and phone are not.
        log.info("Published {} for patientId={}", routingKey, patientId);
    }
}
