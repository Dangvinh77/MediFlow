package com.mediflow.notification;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mediflow.notification.application.dto.command.PrescriptionNoticeCommand;
import com.mediflow.notification.application.port.in.ReactToPrescriptionNoticeUseCase;
import com.mediflow.notification.application.port.in.SendNotificationUseCase;
import com.mediflow.notification.application.port.out.CareNotificationRepositoryPort;
import com.mediflow.notification.application.port.out.NotificationSourcePort;
import com.mediflow.notification.application.service.NotificationTemplates;
import com.mediflow.notification.application.service.PrescriptionNoticeService;
import com.mediflow.notification.application.service.SurgeryPaymentNoticeService;
import com.mediflow.notification.domain.exception.NotificationEventConflictException;
import com.mediflow.notification.domain.model.CareNotificationIntent;
import com.mediflow.notification.infrastructure.messaging.PrescriptionNoticeDecoder;
import com.mediflow.notification.infrastructure.messaging.SurgeryPaymentNoticeDecoder;
import com.mediflow.notification.messaging.consumer.NotificationEventConsumer;
import com.mediflow.notification.messaging.consumer.PrescriptionNoticeWireHandler;
import com.mediflow.notification.messaging.consumer.PrescriptionPaymentNoticeWireHandler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

class PrescriptionNoticeContractTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final PrescriptionNoticeDecoder decoder = new PrescriptionNoticeDecoder(mapper);

    @ParameterizedTest @ValueSource(strings = {"created", "filled", "cancelled", "expired", "dispense.failed"})
    void originalPharmacyBytesBecomePrivateInAppIntentWithoutClinicalOrRefundClaims(String kind) throws Exception {
        var command = decoder.decode("prescription." + kind, fixture(kind));
        var history = mock(CareNotificationRepositoryPort.class);
        var sources = mock(NotificationSourcePort.class);
        when(history.claim(any(), anyString(), anyString())).thenReturn(true);
        when(sources.claim(anyString(), any(), anyString())).thenReturn(true);
        new PrescriptionNoticeService(history, sources).receive(command);
        var intent = ArgumentCaptor.forClass(CareNotificationIntent.class);
        verify(history).deliverInApp(intent.capture());
        assertThat(intent.getValue().patientId()).isEqualTo(UUID.fromString("10000000-0000-4000-8000-000000000002"));
        assertThat(intent.getValue().sourceId()).isEqualTo(command.prescriptionId());
        assertThat(intent.getValue().content()).doesNotContain("Paracetamol", "2000", "đã hoàn", "đã thanh toán");
        assertThat(intent.getValue().templateKey()).startsWith("OUTPATIENT_PRESCRIPTION_");
        assertThat(command.deliveryFingerprint()).hasSize(64);
        assertThat(command.sourceFingerprint()).hasSize(64);
        verify(sources).claim(command.eventType(), command.prescriptionId(), command.sourceFingerprint());
    }

    @Test void newDeliveryIdKeepsTheSameSemanticOperationFingerprint() throws Exception {
        var original = decoder.decode("prescription.filled", fixture("filled"));
        ObjectNode root = root("filled");
        root.put("eventId", UUID.randomUUID().toString());
        root.put("correlationId", "another-delivery");
        var retry = decoder.decode("prescription.filled", mapper.writeValueAsBytes(root));
        assertThat(retry.sourceFingerprint()).isEqualTo(original.sourceFingerprint());
        assertThat(retry.deliveryFingerprint()).isNotEqualTo(original.deliveryFingerprint());
    }

    @ParameterizedTest @ValueSource(strings = {"event", "operation"})
    void repeatedEventOrRepeatedOperationDoesNotDeliverAgain(String duplicate) throws Exception {
        var history = mock(CareNotificationRepositoryPort.class);
        var sources = mock(NotificationSourcePort.class);
        when(history.claim(any(), anyString(), anyString())).thenReturn(duplicate.equals("operation"));
        when(sources.claim(anyString(), any(), anyString())).thenReturn(false);
        new PrescriptionNoticeService(history, sources).receive(decoder.decode("prescription.filled", fixture("filled")));
        verify(history, never()).deliverInApp(any());
        if (duplicate.equals("event")) verifyNoInteractions(sources);
    }

    @ParameterizedTest @ValueSource(strings = {"recordId", "patientId", "departmentId", "careEpisodeId", "sourceId", "dispenseId"})
    void missingAuthoritativeIdentityIsRejected(String field) throws Exception {
        ObjectNode root = root("filled");
        ((ObjectNode) root.path("payload")).remove(field);
        reject(root);
    }

    @ParameterizedTest @ValueSource(strings = {"created", "filled", "cancelled", "expired", "dispense.failed"})
    void admissionFixturesRemainDeferred(String kind) throws Exception {
        byte[] admission = Files.readAllBytes(Path.of("../pharmacy-service/src/test/resources/contracts/care-finance-v1/prescription." + kind + ".v1.json"));
        assertThatThrownBy(() -> decoder.decode("prescription." + kind, admission)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void strictWireRejectsWrongProducerVersionScopeTimeAndSnapshot() throws Exception {
        ObjectNode root = root("filled"); root.put("producer", "billing-service"); reject(root);
        root = root("filled"); root.put("version", 4294967297L); reject(root);
        root = root("filled"); payload(root).put("sourceId", UUID.randomUUID().toString()); reject(root);
        root = root("filled"); payload(root).put("careEpisodeType", "ADMISSION"); reject(root);
        root = root("filled"); payload(root).put("filledAt", "2026-10-01T08:11:00Z"); reject(root);
        root = root("filled"); payload(root).put("totalAmount", 1); reject(root);
        root = root("filled"); payload(root).put("unexpected", true); reject(root);
        root = root("filled"); payload(root).put("patientId", "1-1-1-1-1"); reject(root);
        root = root("filled"); ((ObjectNode) payload(root).path("items").get(0)).put("quantity", 4294967297L); reject(root);
    }

    @Test void duplicateJsonKeysTrailingJsonAndOversizedBodyAreRejected() throws Exception {
        String valid = new String(fixture("filled"), StandardCharsets.UTF_8);
        assertThatThrownBy(() -> decoder.decode("prescription.filled", (valid + " {}").getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(IllegalArgumentException.class);
        String duplicate = valid.replace("\"version\": 1", "\"version\": 0, \"version\": 1");
        assertThat(duplicate).isNotEqualTo(valid);
        assertThatThrownBy(() -> decoder.decode("prescription.filled", duplicate.getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> decoder.decode("prescription.filled", new byte[1_048_577])).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void existingReaderRoutesRxV1ToItsOwnHandlerAndDisabledDoesNotFallback() throws Exception {
        var notices = mock(ReactToPrescriptionNoticeUseCase.class);
        var legacy = mock(SendNotificationUseCase.class);
        var handler = new PrescriptionNoticeWireHandler(decoder, notices);
        var consumer = new NotificationEventConsumer(legacy, new NotificationTemplates(), mapper, null, null, handler);
        var properties = new MessageProperties(); properties.setReceivedRoutingKey("prescription.filled");
        var message = new Message(fixture("filled"), properties);
        consumer.onMessage(message);
        verify(notices).receive(any());
        verifyNoInteractions(legacy);
        var disabled = new NotificationEventConsumer(legacy, new NotificationTemplates(), mapper);
        assertThatThrownBy(() -> disabled.onMessage(message)).isInstanceOf(AmqpRejectAndDontRequeueException.class);
        verifyNoInteractions(legacy);
    }

    @Test void conflictsAreDeadLetteredButDatabaseFailuresRemainRetriable() throws Exception {
        var notices = mock(ReactToPrescriptionNoticeUseCase.class);
        var handler = new PrescriptionNoticeWireHandler(decoder, notices);
        doThrow(new NotificationEventConflictException()).when(notices).receive(any());
        assertThatThrownBy(() -> handler.receive("prescription.filled", fixture("filled")))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);
        reset(notices);
        doThrow(new org.springframework.dao.TransientDataAccessResourceException("storage unavailable")).when(notices).receive(any());
        assertThatThrownBy(() -> handler.receive("prescription.filled", fixture("filled")))
                .isInstanceOf(org.springframework.dao.TransientDataAccessResourceException.class);
    }

    @Test void existingQueueReceivesBillingRxRequestWithoutActivatingSurgeryOrClaimingPayment() throws Exception {
        var history = mock(CareNotificationRepositoryPort.class);
        var sources = mock(NotificationSourcePort.class);
        when(history.claim(any(), anyString(), anyString())).thenReturn(true);
        when(sources.claim(anyString(), any(), anyString())).thenReturn(true);
        var handler = new PrescriptionPaymentNoticeWireHandler(new SurgeryPaymentNoticeDecoder(mapper),
                new SurgeryPaymentNoticeService(history, sources));
        var legacy = mock(SendNotificationUseCase.class);
        var consumer = new NotificationEventConsumer(legacy, new NotificationTemplates(), mapper, null, null, null, handler);
        var properties = new MessageProperties(); properties.setReceivedRoutingKey("invoice.created");
        consumer.onMessage(new Message(paymentRequestFixture(), properties));
        var capture = ArgumentCaptor.forClass(CareNotificationIntent.class);
        verify(history).deliverInApp(capture.capture());
        assertThat(capture.getValue().templateKey()).isEqualTo("PRESCRIPTION_PAYMENT_REQUEST");
        assertThat(capture.getValue().content()).contains("2000", "không phải biên nhận", "không xác nhận thuốc đã được cấp");
        verifyNoInteractions(legacy);
        var disabled = new NotificationEventConsumer(legacy, new NotificationTemplates(), mapper);
        assertThatThrownBy(() -> disabled.onMessage(new Message(paymentRequestFixture(), properties)))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);
    }

    @ParameterizedTest @ValueSource(strings = {"admission", "outpatient"})
    void outpatientRxGateDoesNotActivateSurgeryPaymentRequests(String context) throws Exception {
        var notices = mock(com.mediflow.notification.application.port.in.ReactToSurgeryPaymentNoticeUseCase.class);
        var handler = new PrescriptionPaymentNoticeWireHandler(new SurgeryPaymentNoticeDecoder(mapper), notices);
        assertThatThrownBy(() -> handler.receive("invoice.created", SurgeryPaymentNoticeContractTest.fixture(context)))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);
        verifyNoInteractions(notices);
    }

    @Test void billingRequestConflictIsPermanentButStoreOutageRemainsRetriable() throws Exception {
        var notices = mock(com.mediflow.notification.application.port.in.ReactToSurgeryPaymentNoticeUseCase.class);
        var handler = new PrescriptionPaymentNoticeWireHandler(new SurgeryPaymentNoticeDecoder(mapper), notices);
        doThrow(new NotificationEventConflictException()).when(notices).receive(any());
        assertThatThrownBy(() -> handler.receive("invoice.created", paymentRequestFixture()))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);
        reset(notices);
        doThrow(new org.springframework.dao.TransientDataAccessResourceException("store unavailable")).when(notices).receive(any());
        assertThatThrownBy(() -> handler.receive("invoice.created", paymentRequestFixture()))
                .isInstanceOf(org.springframework.dao.TransientDataAccessResourceException.class);
    }

    static byte[] paymentRequestFixture() throws Exception {
        return Files.readAllBytes(Path.of("../billing-service/src/test/resources/contracts/ledger-v1/invoice-prescription.json"));
    }

    static byte[] fixture(String kind) throws Exception {
        return Files.readAllBytes(Path.of("../pharmacy-service/src/test/resources/contracts/care-finance-v1/prescription." + kind + ".outpatient.v1.json"));
    }
    private ObjectNode root(String kind) throws Exception { return (ObjectNode) mapper.readTree(fixture(kind)); }
    private ObjectNode payload(ObjectNode root) { return (ObjectNode) root.path("payload"); }
    private void reject(ObjectNode root) throws Exception {
        byte[] body = mapper.writeValueAsBytes(root);
        assertThatThrownBy(() -> decoder.decode("prescription.filled", body)).isInstanceOf(IllegalArgumentException.class);
    }
}
