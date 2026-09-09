package com.mediflow.clinical.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.mediflow.clinical.application.event.AppointmentCreatedEvent;
import com.mediflow.clinical.application.event.AppointmentStatusChangedEvent;
import com.mediflow.clinical.application.event.DiagnosisAddedEvent;
import com.mediflow.clinical.application.event.MedicalRecordCreatedEvent;
import com.mediflow.clinical.domain.model.AppointmentStatus;

class ClinicalEventPublisherAdapterTest {

    private static final String EXCHANGE = "mediflow.events";

    private final RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
    private final ClinicalEventPublisherAdapter adapter = new ClinicalEventPublisherAdapter(rabbitTemplate);

    @AfterEach
    void clearTransactionSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void publishAppointmentCreated_sendsWithContractRoutingKey() {
        AppointmentCreatedEvent event = appointmentCreatedEvent();

        adapter.publishAppointmentCreated(event);

        verify(rabbitTemplate).convertAndSend(EXCHANGE, "appointment.created", event);
    }

    @Test
    void publishAppointmentStatusChanged_sendsWithContractRoutingKey() {
        AppointmentStatusChangedEvent event = appointmentStatusChangedEvent();

        adapter.publishAppointmentStatusChanged(event);

        verify(rabbitTemplate).convertAndSend(EXCHANGE, "appointment.status.changed", event);
    }

    @Test
    void publishMedicalRecordCreated_sendsWithContractRoutingKey() {
        MedicalRecordCreatedEvent event = medicalRecordCreatedEvent();

        adapter.publishMedicalRecordCreated(event);

        verify(rabbitTemplate).convertAndSend(EXCHANGE, "medicalrecord.created", event);
    }

    @Test
    void publishDiagnosisAdded_sendsWithContractRoutingKey() {
        DiagnosisAddedEvent event = diagnosisAddedEvent();

        adapter.publishDiagnosisAdded(event);

        verify(rabbitTemplate).convertAndSend(EXCHANGE, "diagnosis.added", event);
    }

    @Test
    void publishWithinTransaction_sendsOnlyAfterCommit() {
        TransactionSynchronizationManager.initSynchronization();
        AppointmentCreatedEvent event = appointmentCreatedEvent();

        adapter.publishAppointmentCreated(event);

        verifyNoInteractions(rabbitTemplate);
        List<TransactionSynchronization> synchronizations =
                TransactionSynchronizationManager.getSynchronizations();
        assertThat(synchronizations).hasSize(1);

        synchronizations.forEach(TransactionSynchronization::afterCommit);

        verify(rabbitTemplate).convertAndSend(EXCHANGE, "appointment.created", event);
    }

    @Test
    void publishWithinRolledBackTransaction_sendsNothing() {
        TransactionSynchronizationManager.initSynchronization();

        adapter.publishMedicalRecordCreated(medicalRecordCreatedEvent());
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(synchronization -> synchronization.afterCompletion(
                        TransactionSynchronization.STATUS_ROLLED_BACK));

        verifyNoInteractions(rabbitTemplate);
    }

    private AppointmentCreatedEvent appointmentCreatedEvent() {
        return new AppointmentCreatedEvent(
                UUID.randomUUID(), Instant.now(), "correlation-test",
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                LocalDate.now(), LocalTime.of(9, 30));
    }

    private AppointmentStatusChangedEvent appointmentStatusChangedEvent() {
        return new AppointmentStatusChangedEvent(
                UUID.randomUUID(), Instant.now(), "correlation-test",
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                AppointmentStatus.ARRIVED);
    }

    private MedicalRecordCreatedEvent medicalRecordCreatedEvent() {
        return new MedicalRecordCreatedEvent(
                UUID.randomUUID(), Instant.now(), "correlation-test",
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "Influenza", LocalDate.now());
    }

    private DiagnosisAddedEvent diagnosisAddedEvent() {
        return new DiagnosisAddedEvent(
                UUID.randomUUID(), Instant.now(), "correlation-test",
                UUID.randomUUID(), "J10.1", "Influenza");
    }
}
