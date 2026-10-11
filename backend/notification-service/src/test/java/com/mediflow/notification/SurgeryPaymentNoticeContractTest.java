package com.mediflow.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mediflow.notification.application.port.out.CareNotificationRepositoryPort;
import com.mediflow.notification.application.port.out.NotificationSourcePort;
import com.mediflow.notification.application.service.SurgeryPaymentNoticeService;
import com.mediflow.notification.domain.model.CareNotificationIntent;
import com.mediflow.notification.infrastructure.messaging.SurgeryPaymentNoticeDecoder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class SurgeryPaymentNoticeContractTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final SurgeryPaymentNoticeDecoder decoder = new SurgeryPaymentNoticeDecoder(mapper);
    @ParameterizedTest @ValueSource(strings = {"admission", "outpatient", "prescription"})
    void decode_actualBillingFixture_privateRequestNotReceipt(String context) throws Exception {
        var command = decoder.decode("invoice.created", fixture(context));
        assertThat(command.totalAmount()).isEqualByComparingTo(context.equals("prescription") ? "2000.00" : "100.00"); assertThat(command.currency()).isEqualTo("VND");
        var history = mock(CareNotificationRepositoryPort.class); var sources = mock(NotificationSourcePort.class);
        when(history.claim(command.eventId(), "invoice.created", command.deliveryFingerprint())).thenReturn(true);
        when(sources.claim("invoice.created", command.paymentRequestId(), command.sourceFingerprint())).thenReturn(true);
        new SurgeryPaymentNoticeService(history, sources).receive(command);
        var capture = org.mockito.ArgumentCaptor.forClass(CareNotificationIntent.class); verify(history).deliverInApp(capture.capture());
        assertThat(capture.getValue().templateKey()).isEqualTo(context.equals("prescription") ? "PRESCRIPTION_PAYMENT_REQUEST" : "SURGERY_PAYMENT_REQUEST");
        assertThat(capture.getValue().content()).contains("không phải biên nhận");
        if (context.equals("prescription")) assertThat(capture.getValue().content()).contains("không xác nhận thuốc đã được cấp").doesNotContain("phẫu thuật", "lịch mổ");
        else assertThat(capture.getValue().content()).contains("xác nhận lịch mổ");
        assertThat(capture.getValue().patientId()).isEqualTo(command.patientId());
    }
    @ParameterizedTest @ValueSource(strings = {"version", "producer", "purpose", "admissionId", "patientId", "totalAmount", "currency", "occurredAt", "expiresAt", "paymentRequestId"})
    void decode_invalidBillingContract_noFallbackOrPayloadCause(String field) throws Exception {
        var root = (ObjectNode)mapper.readTree(fixture("admission")); var p = (ObjectNode)root.get("payload");
        switch (field) {
            case "version" -> root.put(field, 2);
            case "producer" -> root.put(field, "surgery-service");
            case "purpose" -> p.put(field, "EXAM");
            case "admissionId" -> p.put(field, UUID.randomUUID().toString());
            case "patientId" -> p.put(field, "1-1-1-1-1");
            case "totalAmount" -> p.put(field, "100.00");
            case "currency" -> p.put(field, "USD");
            case "occurredAt" -> root.put(field, "2026-10-08T04:00:00Z");
            case "expiresAt" -> p.put(field, p.path("createdAt").asText());
            case "paymentRequestId" -> p.put(field, p.path("invoiceId").asText());
            default -> throw new AssertionError(field);
        }
        assertThatThrownBy(() -> decoder.decode("invoice.created", mapper.writeValueAsBytes(root)))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Invalid Surgery payment-request notice contract").hasNoCause();
    }
    @Test void consume_duplicateDeliveryAndSemanticSource_neverSendAgain() throws Exception {
        var command = decoder.decode("invoice.created", fixture("admission"));
        var history = mock(CareNotificationRepositoryPort.class); var sources = mock(NotificationSourcePort.class);
        var service = new SurgeryPaymentNoticeService(history, sources);
        service.receive(command); verifyNoInteractions(sources); verify(history, never()).deliverInApp(any());
        when(history.claim(command.eventId(), "invoice.created", command.deliveryFingerprint())).thenReturn(true);
        service.receive(command); verify(sources).claim("invoice.created", command.paymentRequestId(), command.sourceFingerprint()); verify(history, never()).deliverInApp(any());
    }
    @Test void decode_duplicateKeysTrailingAndLegacyFlat_rejectRatherThanDowngrade() throws Exception {
        String body = new String(fixture("admission"), java.nio.charset.StandardCharsets.UTF_8);
        for (String invalid : new String[]{body.replace("\"version\": 1", "\"version\": 1, \"version\": 1"), body + " {}", "{\"invoiceId\":\"00000000-0000-4000-8000-000000000012\"}"})
            assertThatThrownBy(() -> decoder.decode("invoice.created", invalid.getBytes(java.nio.charset.StandardCharsets.UTF_8))).isInstanceOf(IllegalArgumentException.class);
    }
    @ParameterizedTest @ValueSource(strings = {"missingRx", "foreignSurgery", "admission", "wrongEpisode", "foreignLab", "foreignExam", "correction", "shortRx", "controlCorrelation"})
    void decode_prescriptionMustHaveOneExactOutpatientTarget(String mutation) throws Exception {
        var root = (ObjectNode)mapper.readTree(fixture("prescription")); var p = (ObjectNode)root.path("payload");
        switch (mutation) {
            case "missingRx" -> p.remove("prescriptionId");
            case "foreignSurgery" -> p.put("surgeryCaseId", UUID.randomUUID().toString());
            case "admission" -> p.put("admissionId", p.path("careEpisodeId").asText());
            case "wrongEpisode" -> p.put("careEpisodeType", "ADMISSION");
            case "foreignLab" -> p.putArray("labTestIds").add(UUID.randomUUID().toString());
            case "foreignExam" -> p.put("recordId", UUID.randomUUID().toString());
            case "correction" -> p.put("supersedesEventId", UUID.randomUUID().toString());
            case "shortRx" -> p.put("prescriptionId", "1-1-1-1-1");
            case "controlCorrelation" -> root.put("correlationId", "bad\ntrace");
        }
        assertThatThrownBy(() -> decoder.decode("invoice.created", mapper.writeValueAsBytes(root))).isInstanceOf(IllegalArgumentException.class);
    }
    static byte[] fixture(String context) throws Exception {
        if (context.equals("prescription")) return Files.readAllBytes(Path.of("../billing-service/src/test/resources/contracts/ledger-v1/invoice-prescription.json"));
        Path path = Path.of("../billing-service/src/test/resources/contracts/ledger-v1/invoice-surgery-" + context + ".json");
        if (!Files.exists(path)) path = Path.of("backend/billing-service/src/test/resources/contracts/ledger-v1/invoice-surgery-" + context + ".json");
        return Files.readAllBytes(path);
    }
}
