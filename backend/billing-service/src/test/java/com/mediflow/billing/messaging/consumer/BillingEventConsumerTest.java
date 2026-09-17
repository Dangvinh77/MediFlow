package com.mediflow.billing.messaging.consumer;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.mediflow.billing.application.event.AppointmentStatusChangedEvent;
import com.mediflow.billing.application.event.LabResultCreatedEvent;
import com.mediflow.billing.application.event.MedicalRecordCreatedEvent;
import com.mediflow.billing.application.event.PrescriptionCreatedEvent;
import com.mediflow.billing.application.event.PrescriptionCancelledEvent;
import com.mediflow.billing.application.event.PrescriptionDispenseFailedEvent;
import com.mediflow.billing.application.event.PrescriptionExpiredEvent;
import com.mediflow.billing.application.event.PrescriptionFilledEvent;
import com.mediflow.billing.application.port.in.AccrueFeeUseCase;
import com.mediflow.billing.application.port.in.SagaCompensationUseCase;
import com.mediflow.billing.infrastructure.config.RabbitConfig;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Kiểm tra {@link BillingEventConsumer} định tuyến đúng các routing key sang đúng in-port
 * (không cần RabbitMQ thật — chỉ dựng {@link Message} thủ công như container thật sẽ đưa vào).
 */
class BillingEventConsumerTest {

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private final AccrueFeeUseCase accrueFeeUseCase = mock(AccrueFeeUseCase.class);
    private final SagaCompensationUseCase sagaCompensationUseCase = mock(SagaCompensationUseCase.class);
    private final BillingEventConsumer consumer =
            new BillingEventConsumer(accrueFeeUseCase, sagaCompensationUseCase, objectMapper);

    @Test
    void medicalRecordCreated_routesToAccrueFeeUseCase() throws Exception {
        UUID recordId = UUID.randomUUID();
        MedicalRecordCreatedEvent event = new MedicalRecordCreatedEvent(
                UUID.randomUUID(), Instant.now(), "cid", recordId, UUID.randomUUID(), UUID.randomUUID(), null);

        consumer.onMessage(messageFor(RabbitConfig.RK_MEDICAL_RECORD_CREATED, event));

        verify(accrueFeeUseCase).onMedicalRecordCreated(eq(event));
        verifyNoInteractions(sagaCompensationUseCase);
    }

    @Test
    void labResultCreated_routesToAccrueFeeUseCase() throws Exception {
        LabResultCreatedEvent event = new LabResultCreatedEvent(
                UUID.randomUUID(), Instant.now(), "cid", UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), "BLOOD_TEST", null);

        consumer.onMessage(messageFor(RabbitConfig.RK_LAB_RESULT_CREATED, event));

        verify(accrueFeeUseCase).onLabResultCreated(eq(event));
    }

    @Test
    void appointmentStatusChanged_routesToAccrueFeeUseCase() throws Exception {
        AppointmentStatusChangedEvent event = new AppointmentStatusChangedEvent(
                UUID.randomUUID(), Instant.now(), "cid", UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), "ARRIVED", null);

        consumer.onMessage(messageFor(RabbitConfig.RK_APPOINTMENT_STATUS_CHANGED, event));

        verify(accrueFeeUseCase).onAppointmentStatusChanged(eq(event));
    }

    @Test
    void prescriptionCreated_routesToAccrueFeeUseCase() throws Exception {
        PrescriptionCreatedEvent event = new PrescriptionCreatedEvent(
                UUID.randomUUID(), Instant.now(), "cid", UUID.randomUUID(), UUID.randomUUID(),
                null, UUID.randomUUID(), java.math.BigDecimal.TEN, java.util.List.of());

        consumer.onMessage(messageFor(RabbitConfig.RK_PRESCRIPTION_CREATED, event));

        verify(accrueFeeUseCase).onPrescriptionCreated(eq(event));
    }

    @Test
    void prescriptionFilled_routesToSagaCompensationUseCase() throws Exception {
        PrescriptionFilledEvent event = new PrescriptionFilledEvent(
                UUID.randomUUID(), Instant.now(), "cid", UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), java.math.BigDecimal.TEN, java.util.List.of());

        consumer.onMessage(messageFor(RabbitConfig.RK_PRESCRIPTION_FILLED, event));

        verify(sagaCompensationUseCase).onPrescriptionFilled(eq(event));
        verifyNoInteractions(accrueFeeUseCase);
    }

    @Test
    void prescriptionDispenseFailed_routesToSagaCompensationUseCase() throws Exception {
        PrescriptionDispenseFailedEvent event = new PrescriptionDispenseFailedEvent(
                UUID.randomUUID(), Instant.now(), "cid", UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), "Hết thuốc", java.util.List.of());

        consumer.onMessage(messageFor(RabbitConfig.RK_PRESCRIPTION_DISPENSE_FAILED, event));

        verify(sagaCompensationUseCase).onDispenseFailed(eq(event));
    }

    @Test
    void prescriptionCancelled_routesToSagaCompensationUseCase() throws Exception {
        PrescriptionCancelledEvent event = new PrescriptionCancelledEvent(
                UUID.randomUUID(), Instant.now(), "cid", UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), "Doctor cancelled");

        consumer.onMessage(messageFor(RabbitConfig.RK_PRESCRIPTION_CANCELLED, event));

        verify(sagaCompensationUseCase).onPrescriptionCancelled(eq(event));
    }

    @Test
    void prescriptionExpired_routesToSagaCompensationUseCase() throws Exception {
        PrescriptionExpiredEvent event = new PrescriptionExpiredEvent(
                UUID.randomUUID(), Instant.now(), "cid", UUID.randomUUID(), UUID.randomUUID(), 2);

        consumer.onMessage(messageFor(RabbitConfig.RK_PRESCRIPTION_EXPIRED, event));

        verify(sagaCompensationUseCase).onPrescriptionExpired(eq(event));
    }

    @Test
    void unknownRoutingKey_isRejectedForRetryAndDeadLetter() {
        Message message = new Message("{}".getBytes(java.nio.charset.StandardCharsets.UTF_8),
                propertiesFor("some.other.event"));

        assertThatThrownBy(() -> consumer.onMessage(message))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(accrueFeeUseCase, sagaCompensationUseCase);
    }

    private Message messageFor(String routingKey, Object payload) throws Exception {
        byte[] body = objectMapper.writeValueAsBytes(payload);
        return new Message(body, propertiesFor(routingKey));
    }

    private MessageProperties propertiesFor(String routingKey) {
        MessageProperties properties = new MessageProperties();
        properties.setReceivedRoutingKey(routingKey);
        return properties;
    }
}
