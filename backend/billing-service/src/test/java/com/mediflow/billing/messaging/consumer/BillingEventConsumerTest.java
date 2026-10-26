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
import com.mediflow.billing.application.event.SurgeryCancelledEvent;
import com.mediflow.billing.application.event.SurgeryCaseCreatedEvent;
import com.mediflow.billing.application.event.SurgeryCompletedEvent;
import com.mediflow.billing.application.port.in.AccrueFeeUseCase;
import com.mediflow.billing.application.port.in.SagaCompensationUseCase;
import com.mediflow.billing.application.port.in.SurgeryChargeUseCase;
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

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule())
            .disable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    private final AccrueFeeUseCase accrueFeeUseCase = mock(AccrueFeeUseCase.class);
    private final SagaCompensationUseCase sagaCompensationUseCase = mock(SagaCompensationUseCase.class);
    private final SurgeryChargeUseCase surgeryChargeUseCase = mock(SurgeryChargeUseCase.class);
    private final BillingEventConsumer consumer =
            new BillingEventConsumer(accrueFeeUseCase, sagaCompensationUseCase, surgeryChargeUseCase, objectMapper);

    @Test
    void versionedMedicalRecordFactDoesNotAccrueAnotherExamFee() throws Exception {
        outpatientConsumer().onMessage(fixture("medicalrecord.created"));
        verifyNoInteractions(accrueFeeUseCase, sagaCompensationUseCase, surgeryChargeUseCase);
    }

    @Test
    void versionedLabResultFactDoesNotAccrueAnotherLabFeeEvenWhenRedelivered() throws Exception {
        var current = outpatientConsumer();
        current.onMessage(fixture("lab.result.created"));
        current.onMessage(fixture("lab.result.created"));
        verifyNoInteractions(accrueFeeUseCase, sagaCompensationUseCase, surgeryChargeUseCase);
    }

    @Test
    void versionedFactsCannotFallBackToLegacyWhenIssuerIsDisabled() throws Exception {
        for (var key : java.util.List.of("medicalrecord.created", "lab.result.created")) {
            var message = fixture(key);
            assertThatThrownBy(() -> consumer.onMessage(message)).isInstanceOf(IllegalArgumentException.class);
        }
        verifyNoInteractions(accrueFeeUseCase);
    }

    @Test
    void malformedVersionedFactsAreRejectedWithoutLegacyEffects() throws Exception {
        var current = outpatientConsumer();
        for (var key : java.util.List.of("medicalrecord.created", "lab.result.created")) {
            var original = fixture(key);
            for (var mutation : java.util.List.of("producer", "version", "payload", "careEpisodeType", "patientId")) {
                var tree = (com.fasterxml.jackson.databind.node.ObjectNode) objectMapper.readTree(original.getBody());
                switch (mutation) {
                    case "producer" -> tree.put("producer", "untrusted-service");
                    case "version" -> tree.put("version", 2);
                    case "payload" -> tree.remove("payload");
                    case "careEpisodeType" -> ((com.fasterxml.jackson.databind.node.ObjectNode) tree.get("payload"))
                            .put("careEpisodeType", "ADMISSION");
                    case "patientId" -> ((com.fasterxml.jackson.databind.node.ObjectNode) tree.get("payload"))
                            .remove("patientId");
                    default -> throw new AssertionError(mutation);
                }
                var malformed = new Message(objectMapper.writeValueAsBytes(tree), propertiesFor(key));
                assertThatThrownBy(() -> current.onMessage(malformed)).isInstanceOf(IllegalArgumentException.class);
            }
        }
        verifyNoInteractions(accrueFeeUseCase);
    }

    @Test
    void configuredV1IssuersDoNotAllowFlatLifecycleFactsToCreateLegacyFees() throws Exception {
        var event = new MedicalRecordCreatedEvent(UUID.randomUUID(), Instant.now(), "cid",
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), null);
        assertThatThrownBy(() -> outpatientConsumer().onMessage(messageFor("medicalrecord.created", event)))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(accrueFeeUseCase);
    }

    @Test
    void duplicateKeysTrailingJsonAndMismatchedExamSourceAreRejected() throws Exception {
        var key = "medicalrecord.created";
        var original = fixture(key);
        var json = new String(original.getBody(), java.nio.charset.StandardCharsets.UTF_8);
        var mismatched = (com.fasterxml.jackson.databind.node.ObjectNode) objectMapper.readTree(json);
        ((com.fasterxml.jackson.databind.node.ObjectNode) mismatched.get("payload"))
                .put("sourceId", "00000000-0000-4000-8000-000000000099");
        for (var raw : java.util.List.of(json + " {}", json.replace("\"version\": 1", "\"version\": 1, \"version\": 1"),
                objectMapper.writeValueAsString(mismatched))) {
            var message = new Message(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8), propertiesFor(key));
            assertThatThrownBy(() -> outpatientConsumer().onMessage(message)).isInstanceOf(IllegalArgumentException.class);
        }
        verifyNoInteractions(accrueFeeUseCase);
    }

    private BillingEventConsumer outpatientConsumer() {
        return new BillingEventConsumer(accrueFeeUseCase, sagaCompensationUseCase, surgeryChargeUseCase,
                objectMapper, null, null, null, null, null, mock(LabTestChargeConsumer.class),
                null, null, mock(ExamChargeConsumer.class), null);
    }

    private Message fixture(String key) throws Exception {
        try (var input = getClass().getResourceAsStream("/contracts/outpatient-lifecycle/" + key + ".v1.json")) {
            return new Message(java.util.Objects.requireNonNull(input).readAllBytes(), propertiesFor(key));
        }
    }

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
    void surgeryCaseCreated_routesToSurgeryChargeUseCase() throws Exception {
        var payload = new SurgeryCaseCreatedEvent.Payload(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), "ADMISSION", UUID.randomUUID(), UUID.randomUUID(), null, java.util.List.of(), Instant.now());
        SurgeryCaseCreatedEvent event = new SurgeryCaseCreatedEvent(UUID.randomUUID(), Instant.now(), "cid", payload);

        consumer.onMessage(messageFor(RabbitConfig.RK_SURGERY_CASE_CREATED, event));

        verify(surgeryChargeUseCase).onSurgeryCaseCreated(eq(event));
        verifyNoInteractions(accrueFeeUseCase, sagaCompensationUseCase);
    }

    @Test
    void surgeryCompleted_routesToSurgeryChargeUseCase() throws Exception {
        var payload = new SurgeryCompletedEvent.Payload(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), null, UUID.randomUUID(), java.util.List.of(), Instant.now(), Instant.now(), Instant.now());
        SurgeryCompletedEvent event = new SurgeryCompletedEvent(UUID.randomUUID(), Instant.now(), "cid", payload);

        consumer.onMessage(messageFor(RabbitConfig.RK_SURGERY_COMPLETED, event));

        verify(surgeryChargeUseCase).onSurgeryCompleted(eq(event));
    }

    @Test
    void surgeryCancelled_routesToSurgeryChargeUseCase() throws Exception {
        var payload = new SurgeryCancelledEvent.Payload(UUID.randomUUID(), UUID.randomUUID(), null, UUID.randomUUID(),
                "BEFORE_START", "Patient request", UUID.randomUUID(), Instant.now());
        SurgeryCancelledEvent event = new SurgeryCancelledEvent(UUID.randomUUID(), Instant.now(), "cid", payload);

        consumer.onMessage(messageFor(RabbitConfig.RK_SURGERY_CANCELLED, event));

        verify(surgeryChargeUseCase).onSurgeryCancelled(eq(event));
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
