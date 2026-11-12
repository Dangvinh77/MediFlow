package com.mediflow.notification.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mediflow.notification.application.port.out.CareNotificationRepositoryPort;
import com.mediflow.notification.application.service.PatientResultNoticeService;
import com.mediflow.notification.domain.model.CareNotificationIntent;

/** One in-app notice per (labId, resultVersion), pointing the patient at the current result. */
class PatientResultAmendedNoticeTest {
    static final UUID LAB = UUID.fromString("00000000-0000-4000-8000-000000000002");
    final ObjectMapper mapper = new ObjectMapper();
    final CareNotificationRepositoryPort history = mock(CareNotificationRepositoryPort.class);
    final PatientResultNoticeDecoder decoder = new PatientResultNoticeDecoder(mapper, new PatientResultNoticeService(history));

    ObjectNode fixture() throws Exception {
        return (ObjectNode) mapper.readTree(Files.readString(Path.of(
                "../lab-service/src/test/resources/contracts/lab.result.amended.v1.json"), StandardCharsets.UTF_8));
    }

    byte[] bytes(ObjectNode node) throws Exception {
        return mapper.writeValueAsBytes(node);
    }

    @Test void amendedResultNotifiesThePatientOfTheCurrentVersion() throws Exception {
        when(history.claim(any(), anyString(), anyString())).thenReturn(true);
        decoder.receive("lab.result.amended", bytes(fixture()));

        var claimed = ArgumentCaptor.forClass(UUID.class);
        verify(history).claim(claimed.capture(), org.mockito.ArgumentMatchers.eq("lab.result.amended"), anyString());
        assertThat(claimed.getValue()).isEqualTo(
                UUID.nameUUIDFromBytes(("lab.result.amended:" + LAB + ":2").getBytes(StandardCharsets.UTF_8)));
        var intent = ArgumentCaptor.forClass(CareNotificationIntent.class);
        verify(history).deliverInApp(intent.capture());
        assertThat(intent.getValue().title()).isEqualTo("Kết quả xét nghiệm đã được đính chính");
        assertThat(intent.getValue().content()).contains("bản mới nhất").contains("Kết quả xét nghiệm");
        assertThat(intent.getValue().sourceId()).isEqualTo(LAB);
    }

    @Test void aSecondEventForTheSameRevisionClaimsTheSameNotice() throws Exception {
        when(history.claim(any(), anyString(), anyString())).thenReturn(true, false);
        var first = fixture();
        var second = fixture().put("eventId", UUID.randomUUID().toString());
        decoder.receive("lab.result.amended", bytes(first));
        decoder.receive("lab.result.amended", bytes(second));

        var ids = ArgumentCaptor.forClass(UUID.class);
        var prints = ArgumentCaptor.forClass(String.class);
        verify(history, times(2)).claim(ids.capture(), anyString(), prints.capture());
        assertThat(ids.getAllValues()).containsOnly(ids.getAllValues().get(0));
        assertThat(prints.getAllValues()).containsOnly(prints.getAllValues().get(0));
        verify(history, times(1)).deliverInApp(any());
    }

    @Test void aLaterRevisionIsANewNotice() throws Exception {
        when(history.claim(any(), anyString(), anyString())).thenReturn(true);
        var third = fixture();
        ((ObjectNode) third.get("payload")).put("resultVersion", 3).put("previousResultVersion", 2);
        decoder.receive("lab.result.amended", bytes(third));
        var claimed = ArgumentCaptor.forClass(UUID.class);
        verify(history).claim(claimed.capture(), anyString(), anyString());
        assertThat(claimed.getValue()).isEqualTo(
                UUID.nameUUIDFromBytes(("lab.result.amended:" + LAB + ":3").getBytes(StandardCharsets.UTF_8)));
    }

    @Test void malformedAmendmentIsRejectedWithoutANotice() throws Exception {
        var node = fixture();
        ((ObjectNode) node.get("payload")).put("resultVersion", 1);
        assertThatThrownBy(() -> decoder.receive("lab.result.amended", bytes(node)))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);
        verify(history, never()).claim(any(), anyString(), anyString());
    }
}
