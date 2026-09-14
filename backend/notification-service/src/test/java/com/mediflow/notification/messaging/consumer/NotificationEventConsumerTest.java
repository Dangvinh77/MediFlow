package com.mediflow.notification.messaging.consumer;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.mediflow.notification.application.port.in.NotificationTrigger;
import com.mediflow.notification.application.port.in.SendNotificationUseCase;
import com.mediflow.notification.application.service.NotificationTemplates;
import com.mediflow.notification.infrastructure.config.RabbitConfig;
import com.mediflow.notification.messaging.consumer.payload.AppointmentCreatedPayload;
import com.mediflow.notification.messaging.consumer.payload.PatientCreatedPayload;
import com.mediflow.notification.messaging.consumer.payload.PaymentCompletedPayload;
import com.mediflow.notification.messaging.consumer.payload.PaymentFailedPayload;
import com.mediflow.notification.messaging.consumer.payload.PrescriptionFilledPayload;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Kiểm tra {@link NotificationEventConsumer} định tuyến đúng 6 routing key, dựng
 * {@link NotificationTrigger} đúng từ template — không cần RabbitMQ thật.
 */
class NotificationEventConsumerTest {

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private final SendNotificationUseCase sendNotificationUseCase = mock(SendNotificationUseCase.class);
    private final NotificationEventConsumer consumer =
            new NotificationEventConsumer(sendNotificationUseCase, new NotificationTemplates(), objectMapper);

    @Test
    void patientCreated_buildsTriggerWithEmailAndPhoneFromPayload() throws Exception {
        UUID patientId = UUID.randomUUID();
        PatientCreatedPayload payload = new PatientCreatedPayload(
                UUID.randomUUID(), Instant.now(), "cid", patientId, "Nguyễn Văn A", "a@example.com", "0912345678");

        consumer.onMessage(messageFor(RabbitConfig.RK_PATIENT_CREATED, payload));

        verify(sendNotificationUseCase).handleEvent(argThat(trigger ->
                trigger.eventId().equals(payload.eventId())
                        && trigger.routingKey().equals(RabbitConfig.RK_PATIENT_CREATED)
                        && trigger.patientId().equals(patientId)
                        && trigger.email().equals("a@example.com")
                        && trigger.phone().equals("0912345678")
                        && trigger.title().equals("Chào mừng đến với MediFlow")
                        && trigger.content().contains("Nguyễn Văn A")));
    }

    @Test
    void appointmentCreated_fillsDateAndTimeVars() throws Exception {
        AppointmentCreatedPayload payload = new AppointmentCreatedPayload(
                UUID.randomUUID(), Instant.now(), "cid", UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), LocalDate.of(2026, 9, 20), LocalTime.of(9, 30));

        consumer.onMessage(messageFor(RabbitConfig.RK_APPOINTMENT_CREATED, payload));

        verify(sendNotificationUseCase).handleEvent(argThat(trigger ->
                trigger.content().contains("2026-09-20") && trigger.content().contains("09:30")
                        && trigger.email() == null && trigger.phone() == null));
    }

    @Test
    void paymentCompleted_fillsInvoiceAndAmountVars() throws Exception {
        UUID invoiceId = UUID.randomUUID();
        PaymentCompletedPayload payload = new PaymentCompletedPayload(
                UUID.randomUUID(), Instant.now(), "cid", invoiceId, UUID.randomUUID(),
                java.math.BigDecimal.valueOf(150000));

        consumer.onMessage(messageFor(RabbitConfig.RK_PAYMENT_COMPLETED, payload));

        verify(sendNotificationUseCase).handleEvent(argThat(trigger ->
                trigger.content().contains(invoiceId.toString()) && trigger.content().contains("150000")));
    }

    @Test
    void paymentFailed_fillsInvoiceAndReasonVars() throws Exception {
        UUID invoiceId = UUID.randomUUID();
        PaymentFailedPayload payload = new PaymentFailedPayload(
                UUID.randomUUID(), Instant.now(), "cid", invoiceId, UUID.randomUUID(), "Hết thuốc");

        consumer.onMessage(messageFor(RabbitConfig.RK_PAYMENT_FAILED, payload));

        verify(sendNotificationUseCase).handleEvent(argThat(trigger ->
                trigger.content().contains(invoiceId.toString()) && trigger.content().contains("Hết thuốc")));
    }

    @Test
    void prescriptionFilled_staticContentNoContactInfo() throws Exception {
        PrescriptionFilledPayload payload = new PrescriptionFilledPayload(
                UUID.randomUUID(), Instant.now(), "cid", UUID.randomUUID(), UUID.randomUUID());

        consumer.onMessage(messageFor(RabbitConfig.RK_PRESCRIPTION_FILLED, payload));

        verify(sendNotificationUseCase).handleEvent(argThat(trigger ->
                trigger.email() == null && trigger.phone() == null
                        && trigger.title().equals("Thuốc đã sẵn sàng")));
    }

    @Test
    void unknownRoutingKey_isIgnoredWithoutCallingUseCase() throws Exception {
        Message message = new Message("{}".getBytes(StandardCharsets.UTF_8), propertiesFor("some.other.event"));

        consumer.onMessage(message);

        verifyNoInteractions(sendNotificationUseCase);
    }

    private Message messageFor(String routingKey, Object payload) throws Exception {
        return new Message(objectMapper.writeValueAsBytes(payload), propertiesFor(routingKey));
    }

    private MessageProperties propertiesFor(String routingKey) {
        MessageProperties properties = new MessageProperties();
        properties.setReceivedRoutingKey(routingKey);
        return properties;
    }
}
