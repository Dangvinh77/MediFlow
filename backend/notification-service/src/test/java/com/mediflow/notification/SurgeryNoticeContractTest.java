package com.mediflow.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mediflow.notification.application.dto.command.SurgeryNoticeCommand;
import com.mediflow.notification.application.port.out.CareNotificationRepositoryPort;
import com.mediflow.notification.application.port.out.SurgeryNoticeStatePort;
import com.mediflow.notification.application.service.SurgeryNoticeService;
import com.mediflow.notification.domain.model.CareNotificationIntent;
import com.mediflow.notification.infrastructure.messaging.SurgeryNoticeDecoder;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class SurgeryNoticeContractTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final SurgeryNoticeDecoder decoder = new SurgeryNoticeDecoder(mapper);
    @ParameterizedTest @CsvSource({"surgery.ready,admission", "surgery.ready,outpatient",
            "surgery.readiness.invalidated,admission", "surgery.readiness.invalidated,outpatient",
            "surgery.cancelled,admission", "surgery.cancelled,outpatient",
            "surgery.completed,admission", "surgery.completed,outpatient"})
    void decode_actualProducerBytes_retainExactIdentityNotClinicalPayload(String key, String context) throws Exception {
        var command = decoder.decode(key, fixture(key, context));
        assertThat(command.eventType()).isEqualTo(key);
        assertThat(command.context().careEpisodeType()).isEqualTo(context.equals("admission") ? "ADMISSION" : "OUTPATIENT_VISIT");
        assertThat(command.sourceFingerprint()).matches("[a-f0-9]{64}");
        assertThat(command.deliveryFingerprint()).matches("[a-f0-9]{64}");
        assertThat(command.toString()).doesNotContain("complicationsSummary", "performedItems", "Patient request");
    }
    @ParameterizedTest @ValueSource(strings = {"version", "producer", "boolean", "identity", "caseRevision", "scheduleRevision", "episode", "time", "correlation"})
    void decode_invalidSource_rejectsWithoutSensitiveCause(String defect) throws Exception {
        var root = (ObjectNode) mapper.readTree(fixture("surgery.ready", "admission"));
        var payload = (ObjectNode) root.get("payload");
        switch (defect) {
            case "version" -> root.put("version", "1");
            case "producer" -> root.put("producer", "clinical-service");
            case "boolean" -> payload.put("reservationConfirmed", "false");
            case "identity" -> payload.put("patientId", "patient secret");
            case "caseRevision" -> payload.put("caseRevision", 0);
            case "scheduleRevision" -> payload.put("scheduleRevision", 0);
            case "episode" -> payload.put("admissionId", "00000000-0000-4000-8000-000000000099");
            case "time" -> payload.put("plannedEndAt", payload.get("plannedStartAt").textValue());
            case "correlation" -> root.put("correlationId", "x".repeat(121));
            default -> throw new AssertionError(defect);
        }
        assertThatThrownBy(() -> decoder.decode("surgery.ready", mapper.writeValueAsBytes(root)))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Invalid Surgery notification contract").hasNoCause();
    }
    @Test void decode_duplicateKeyAndTrailingJson_reject() throws Exception {
        String valid = new String(fixture("surgery.ready", "admission"), java.nio.charset.StandardCharsets.UTF_8);
        for (String invalid : new String[]{valid.replace("\"version\": 1", "\"version\": 1, \"version\": 1"), valid + " {}"})
            assertThatThrownBy(() -> decoder.decode("surgery.ready", invalid.getBytes(java.nio.charset.StandardCharsets.UTF_8))).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void receive_provisionalReady_isPrivateGenericNotConfirmedBooking() throws Exception {
        var history = mock(CareNotificationRepositoryPort.class); var state = mock(SurgeryNoticeStatePort.class);
        var command = decoder.decode("surgery.ready", fixture("surgery.ready", "admission"));
        when(history.claim(any(), anyString(), anyString())).thenReturn(true); when(state.recordSource(command)).thenReturn(true);
        new SurgeryNoticeService(history, state).receive(command);
        var intent = ArgumentCaptor.forClass(CareNotificationIntent.class); verify(history).deliverInApp(intent.capture());
        assertThat(intent.getValue().templateKey()).isEqualTo("SURGERY_READY");
        assertThat(intent.getValue().content()).contains("chưa phải lịch đặt phòng đã xác nhận").doesNotContain("PRICE", "ITEM", "patientId");
        verify(state).lockExactCase(command.context()); verify(history, never()).recordSuppressedInApp(any());
    }
    @Test void receive_suppressedReady_recordsFailureNotSuccessfulDelivery() throws Exception {
        var history = mock(CareNotificationRepositoryPort.class); var state = mock(SurgeryNoticeStatePort.class);
        var command = decoder.decode("surgery.ready", fixture("surgery.ready", "admission"));
        when(history.claim(any(), anyString(), anyString())).thenReturn(true); when(state.recordSource(command)).thenReturn(true);
        when(state.readyIsSuppressed(command)).thenReturn(true);
        new SurgeryNoticeService(history, state).receive(command);
        verify(history).recordSuppressedInApp(any()); verify(history, never()).deliverInApp(any());
    }
    @Test void receive_duplicateDelivery_doesNotLockOrRepeatHistory() throws Exception {
        var history = mock(CareNotificationRepositoryPort.class); var state = mock(SurgeryNoticeStatePort.class);
        new SurgeryNoticeService(history, state).receive(decoder.decode("surgery.ready", fixture("surgery.ready", "admission")));
        verifyNoInteractions(state); verify(history, never()).deliverInApp(any());
    }
    @Test void receive_semanticDuplicate_newDeliveryDoesNotCreateSecondNotice() throws Exception {
        var history = mock(CareNotificationRepositoryPort.class); var state = mock(SurgeryNoticeStatePort.class);
        when(history.claim(any(), anyString(), anyString())).thenReturn(true);
        new SurgeryNoticeService(history, state).receive(decoder.decode("surgery.ready", fixture("surgery.ready", "admission")));
        verify(history, never()).deliverInApp(any()); verify(history, never()).recordSuppressedInApp(any());
    }
    @Test void receive_completionOnlySuppresses_noClinicalTemplate() throws Exception {
        var history = mock(CareNotificationRepositoryPort.class); var state = mock(SurgeryNoticeStatePort.class);
        var command = decoder.decode("surgery.completed", fixture("surgery.completed", "admission"));
        when(history.claim(any(), anyString(), anyString())).thenReturn(true); when(state.recordSource(command)).thenReturn(true);
        new SurgeryNoticeService(history, state).receive(command);
        verify(state).markTerminal(command); verify(history, never()).deliverInApp(any());
    }
    static byte[] fixture(String key, String context) throws Exception {
        return Files.readAllBytes(Path.of("../surgery-service/src/test/resources/contracts/surgery-outcomes-v1/" + key + "." + context + ".v1.json"));
    }
}
