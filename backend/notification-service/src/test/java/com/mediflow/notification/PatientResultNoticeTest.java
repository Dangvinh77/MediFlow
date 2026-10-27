package com.mediflow.notification;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.notification.application.service.PatientResultNoticeService;
import com.mediflow.notification.application.port.out.CareNotificationRepositoryPort;
import com.mediflow.notification.infrastructure.messaging.PatientResultNoticeDecoder;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;
class PatientResultNoticeTest {
    final CareNotificationRepositoryPort history = mock(CareNotificationRepositoryPort.class);
    final PatientResultNoticeDecoder decoder = new PatientResultNoticeDecoder(new ObjectMapper(),
            new PatientResultNoticeService(history));
    @Test void completedExamLabAndFollowUpArePrivateAndNeedNoAccountOrLogin() {
        for (String type : java.util.List.of("medicalrecord.completed", "lab.result.created", "followup.recommendation.created")) {
            var eventId = UUID.randomUUID();
            var patient = UUID.randomUUID();
            when(history.claim(eq(eventId), eq(type), anyString())).thenReturn(true, false);
            byte[] body = wire(type, eventId, patient, type.startsWith("lab.") ? "lab-service" : "clinical-service");
            decoder.receive(type, body); decoder.receive(type, body);
            verify(history, times(1)).deliverInApp(argThat(n -> n.patientId().equals(patient)
                    && n.sourceEventId().equals(eventId) && !n.content().contains("diagnosis")));
        }
    }
    @Test void wrongProducerAndMissingPatientFailBeforeStorage() {
        assertThatThrownBy(() -> decoder.receive("medicalrecord.completed",
                wire("medicalrecord.completed", UUID.randomUUID(), UUID.randomUUID(), "lab-service")))
            .isInstanceOf(org.springframework.amqp.AmqpRejectAndDontRequeueException.class);
        byte[] missing = new String(wire("lab.result.created", UUID.randomUUID(), UUID.randomUUID(), "lab-service"), StandardCharsets.UTF_8)
            .replace("\"patientId\"", "\"otherId\"").getBytes(StandardCharsets.UTF_8);
        assertThatThrownBy(() -> decoder.receive("lab.result.created", missing))
            .isInstanceOf(org.springframework.amqp.AmqpRejectAndDontRequeueException.class);
        verifyNoInteractions(history);
    }
    private byte[] wire(String type, UUID eventId, UUID patient, String producer) {
        var mapper = new ObjectMapper(); var root = mapper.createObjectNode();
        root.put("eventId", eventId.toString()); root.put("eventType", type); root.put("version", 1);
        root.put("producer", producer); root.put("occurredAt", "2026-10-27T01:00:00Z"); root.put("correlationId", "portal-test");
        var p = root.putObject("payload"); p.put("patientId", patient.toString());
        p.put("recordId", UUID.randomUUID().toString()); p.put("labId", UUID.randomUUID().toString());
        p.put("recommendationId", UUID.randomUUID().toString());
        p.put("completedAt", "2026-10-27T01:00:00Z"); p.put("performedDate", "2026-10-27");
        p.put("careEpisodeType", "OUTPATIENT_VISIT"); p.put("recommendedFrom", "2026-11-01"); p.put("recommendedThrough", "2026-11-07");
        return root.toString().getBytes(StandardCharsets.UTF_8);
    }
}
