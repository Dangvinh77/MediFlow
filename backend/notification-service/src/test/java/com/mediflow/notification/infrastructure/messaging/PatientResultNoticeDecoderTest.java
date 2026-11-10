package com.mediflow.notification.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.notification.application.port.out.CareNotificationRepositoryPort;
import com.mediflow.notification.application.service.PatientResultNoticeService;
import com.mediflow.notification.domain.model.CareNotificationIntent;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class PatientResultNoticeDecoderTest {
    @Test void followUpNoticeDoesNotClaimAReservedSeat() {
        var history = mock(CareNotificationRepositoryPort.class);
        when(history.claim(any(), anyString(), anyString())).thenReturn(true);
        var mapper = new ObjectMapper();
        var root = mapper.createObjectNode();
        root.put("eventId", UUID.randomUUID().toString()).put("eventType", "followup.recommendation.created").put("version", 1)
                .put("producer", "clinical-service").put("occurredAt", "2026-11-10T01:00:00Z").put("correlationId", "trace");
        root.putObject("payload").put("patientId", UUID.randomUUID().toString()).put("recordId", UUID.randomUUID().toString())
                .put("recommendationId", UUID.randomUUID().toString()).put("careEpisodeType", "OUTPATIENT_VISIT")
                .put("recommendedFrom", "2026-11-17").put("recommendedThrough", "2026-11-24");

        new PatientResultNoticeDecoder(mapper, new PatientResultNoticeService(history))
                .receive("followup.recommendation.created", root.toString().getBytes(StandardCharsets.UTF_8));

        var captor = ArgumentCaptor.forClass(CareNotificationIntent.class);
        verify(history).deliverInApp(captor.capture());
        assertThat(captor.getValue().content()).doesNotContain("đã đặt").doesNotContain("giữ suất").contains("liên hệ điều dưỡng để đăng ký");
    }
}
