package com.mediflow.notification;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mediflow.notification.application.port.out.*;
import com.mediflow.notification.application.service.RefundNoticeService;
import com.mediflow.notification.domain.model.CareNotificationIntent;
import com.mediflow.notification.infrastructure.messaging.RefundNoticeDecoder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class RefundNoticeContractTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final RefundNoticeDecoder decoder = new RefundNoticeDecoder(mapper);
    @ParameterizedTest @ValueSource(strings = {"service", "deposit"})
    void consume_actualBillingBytes_privateLinkedRefundNotFinalSettlement(String context) throws Exception {
        var command = decoder.decode("payment.refunded", fixture(context));
        var history = mock(CareNotificationRepositoryPort.class); var sources = mock(NotificationSourcePort.class);
        when(history.claim(command.eventId(), "payment.refunded", command.deliveryFingerprint())).thenReturn(true);
        when(sources.claim("payment.refunded", command.refundTransactionId(), command.sourceFingerprint())).thenReturn(true);
        new RefundNoticeService(history, sources).receive(command);
        var captured = org.mockito.ArgumentCaptor.forClass(CareNotificationIntent.class); verify(history).deliverInApp(captured.capture());
        assertThat(captured.getValue().templateKey()).isEqualTo("PAYMENT_REFUNDED");
        assertThat(captured.getValue().content()).contains(command.originalTransactionId().toString(), "20.00", "không phải quyết toán");
        assertThat(captured.getValue().patientId()).isEqualTo(command.patientId());
    }
    @ParameterizedTest @ValueSource(strings = {"version", "producer", "amount", "currency", "reason", "occurredAt", "patientId", "originalTransactionId", "careEpisodeType", "sourceRevision", "supersedesTransactionId"})
    void decode_invalidContract_causeFreePermanentError(String field) throws Exception {
        var root = (ObjectNode)mapper.readTree(fixture("service")); var p = (ObjectNode)root.get("payload");
        switch (field) {
            case "version" -> root.put(field, 2);
            case "producer" -> root.put(field, "surgery-service");
            case "amount" -> p.put(field, "20.00");
            case "currency" -> p.put(field, "USD");
            case "reason" -> p.put(field, "private clinical text");
            case "occurredAt" -> root.put(field, "2026-10-08T05:00:00Z");
            case "patientId" -> p.put(field, "1-1-1-1-1");
            case "originalTransactionId" -> p.put(field, p.path("refundTransactionId").asText());
            case "careEpisodeType" -> p.put(field, "UNKNOWN");
            case "sourceRevision" -> p.put(field, 2);
            case "supersedesTransactionId" -> p.put(field, UUID.randomUUID().toString());
            default -> throw new AssertionError(field);
        }
        assertThatThrownBy(() -> decoder.decode("payment.refunded", mapper.writeValueAsBytes(root)))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Invalid Billing completed-refund contract").hasNoCause();
    }
    @Test void decode_duplicateKeysTrailingOrFlatBytes_rejects() throws Exception {
        String raw = new String(fixture("service"), StandardCharsets.UTF_8);
        for (String invalid : new String[]{raw.replace("\"version\":1", "\"version\":1,\"version\":1"), raw + " {}", "{}"})
            assertThatThrownBy(() -> decoder.decode("payment.refunded", invalid.getBytes(StandardCharsets.UTF_8))).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void consume_deliveryOrSemanticDuplicate_doesNotSendAgain() throws Exception {
        var command = decoder.decode("payment.refunded", fixture("service"));
        var history = mock(CareNotificationRepositoryPort.class); var sources = mock(NotificationSourcePort.class);
        var service = new RefundNoticeService(history, sources);
        service.receive(command); verifyNoInteractions(sources);
        when(history.claim(command.eventId(), "payment.refunded", command.deliveryFingerprint())).thenReturn(true);
        service.receive(command); verify(history, never()).deliverInApp(any());
    }
    static byte[] fixture(String context) throws Exception {
        var path = Path.of("../billing-service/src/test/resources/contracts/ledger-v1/refund-" + context + ".json");
        if (!Files.exists(path)) path = Path.of("backend/billing-service/src/test/resources/contracts/ledger-v1/refund-" + context + ".json");
        return Files.readAllBytes(path);
    }
}
