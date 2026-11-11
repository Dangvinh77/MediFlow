package com.mediflow.notification.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Binding;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mediflow.notification.application.port.out.CareNotificationRepositoryPort;
import com.mediflow.notification.application.port.out.StaffCriticalNoticePort;
import com.mediflow.notification.domain.model.StaffCriticalNotice;
import com.mediflow.notification.infrastructure.config.LabCriticalNoticeConfiguration;

/** A critical Lab value is addressed to the requesting department's doctors, never to the patient. */
class LabCriticalStaffNoticeTest {
    final ObjectMapper mapper = new ObjectMapper();
    final StaffCriticalNoticePort staff = mock(StaffCriticalNoticePort.class);
    final CareNotificationRepositoryPort patientHistory = mock(CareNotificationRepositoryPort.class);
    final LabCriticalNoticeDecoder decoder = new LabCriticalNoticeDecoder(mapper, staff);

    ObjectNode fixture() throws Exception {
        return (ObjectNode) mapper.readTree(Files.readString(Path.of(
                "../lab-service/src/test/resources/contracts/lab.critical.raised.v1.json"), StandardCharsets.UTF_8));
    }

    @Test void criticalValueIsRecordedForTheDepartment() throws Exception {
        decoder.receive(mapper.writeValueAsBytes(fixture()));
        var notice = ArgumentCaptor.forClass(StaffCriticalNotice.class);
        verify(staff).record(notice.capture());
        assertThat(notice.getValue().noticeId()).isEqualTo(UUID.fromString("00000000-0000-4000-8000-000000000032"));
        assertThat(notice.getValue().recipientDepartmentId())
                .isEqualTo(UUID.fromString("00000000-0000-4000-8000-000000000005"));
        assertThat(notice.getValue().title()).isEqualTo("Giá trị xét nghiệm nguy cấp");
        assertThat(notice.getValue().content()).contains("Kali").contains("7.2").contains("xác nhận");
        verifyNoInteractions(patientHistory);
    }

    @Test void nonCriticalOrMalformedEventIsRejected() throws Exception {
        var high = fixture();
        ((ObjectNode) high.get("payload")).put("flag", "HIGH");
        assertThatThrownBy(() -> decoder.receive(mapper.writeValueAsBytes(high)))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);
        var foreign = fixture().put("producer", "clinical-service");
        assertThatThrownBy(() -> decoder.receive(mapper.writeValueAsBytes(foreign)))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);
        verify(staff, never()).record(any());
    }

    @Test void ownQueueListensOnlyToCriticalLabValues() {
        var keys = new LabCriticalNoticeConfiguration().labCriticalNoticeTopology()
                .getDeclarablesByType(Binding.class).stream().map(Binding::getRoutingKey).toList();
        assertThat(keys).contains("lab.critical.raised").doesNotContain("lab.result.created", "lab.result.amended");
    }
}
