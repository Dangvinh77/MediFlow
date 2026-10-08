package com.mediflow.notification.messaging.consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mediflow.notification.application.dto.command.AdmissionClosedCommand;
import com.mediflow.notification.application.dto.command.AdmissionDepositRequestedCommand;
import com.mediflow.notification.application.dto.command.AdmissionStartedCommand;
import com.mediflow.notification.application.dto.command.SurgeryCancelledNoticeCommand;
import com.mediflow.notification.application.dto.command.SurgeryReadyCommand;
import com.mediflow.notification.application.port.in.ReactToCareProjectionUseCase;

/**
 * Đọc đúng byte thật của producer (inpatient-service, surgery-service) — không copy/đổi tên field
 * (CONTRACT-CARE-PROJECTIONS-01). {@code deposit.topup.required}/{@code settlement.completed} chưa
 * có test vì Billing chưa publish fixture thật cho hai event đó.
 */
class CareProjectionWireHandlerTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final ReactToCareProjectionUseCase projections = mock(ReactToCareProjectionUseCase.class);
    private final CareProjectionWireHandler handler = new CareProjectionWireHandler(mapper, projections, true);

    @Test
    void admissionDepositRequested_consumesExactInpatientProducerBytes() throws Exception {
        handler.receive("admission.deposit.requested", fixture("inpatient", "admission.deposit.requested.v1.json"));

        var captor = ArgumentCaptor.forClass(AdmissionDepositRequestedCommand.class);
        verify(projections).onAdmissionDepositRequested(captor.capture());
        assertThat(captor.getValue().admissionId()).isEqualTo(UUID.fromString("00000000-0000-4000-8000-000000000021"));
        assertThat(captor.getValue().suggestedAmount()).isEqualByComparingTo("150000.00");
        assertThat(captor.getValue().fingerprint()).hasSize(64);
    }

    @Test
    void admissionStarted_consumesExactInpatientProducerBytes() throws Exception {
        handler.receive("admission.started", fixture("inpatient", "admission.started.v1.json"));

        var captor = ArgumentCaptor.forClass(AdmissionStartedCommand.class);
        verify(projections).onAdmissionStarted(captor.capture());
        assertThat(captor.getValue().patientId()).isEqualTo(UUID.fromString("00000000-0000-4000-8000-000000000022"));
    }

    @Test
    void admissionClosed_consumesExactInpatientProducerBytes() throws Exception {
        handler.receive("admission.closed", fixture("inpatient", "admission.closed.v1.json"));

        var captor = ArgumentCaptor.forClass(AdmissionClosedCommand.class);
        verify(projections).onAdmissionClosed(captor.capture());
        assertThat(captor.getValue().admissionId()).isEqualTo(UUID.fromString("00000000-0000-4000-8000-000000000021"));
    }

    @Test
    void surgeryReady_consumesExactSurgeryProducerBytes() throws Exception {
        handler.receive("surgery.ready", surgeryFixture("surgery.ready.admission.v1.json"));

        var captor = ArgumentCaptor.forClass(SurgeryReadyCommand.class);
        verify(projections).onSurgeryReady(captor.capture());
        assertThat(captor.getValue().surgeryCaseId()).isEqualTo(UUID.fromString("00000000-0000-4000-8000-000000000001"));
    }

    @Test
    void surgeryCancelled_consumesExactSurgeryProducerBytes() throws Exception {
        handler.receive("surgery.cancelled", surgeryFixture("surgery.cancelled.admission.v1.json"));

        var captor = ArgumentCaptor.forClass(SurgeryCancelledNoticeCommand.class);
        verify(projections).onSurgeryCancelled(captor.capture());
        assertThat(captor.getValue().cancellationStage()).isEqualTo("BEFORE_START");
        assertThat(captor.getValue().reason()).isEqualTo("Patient request");
    }

    @Test
    void defaultOff_rejectsWithoutCallingUseCase() throws Exception {
        var off = new CareProjectionWireHandler(mapper, projections, false);
        assertThatThrownBy(() -> off.receive("admission.started", fixture("inpatient", "admission.started.v1.json")))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);
        verifyNoInteractions(projections);
    }

    @Test
    void wrongProducer_isRejected() throws Exception {
        var root = (ObjectNode) mapper.readTree(fixture("inpatient", "admission.started.v1.json"));
        root.put("producer", "billing-service");
        assertThatThrownBy(() -> handler.receive("admission.started", mapper.writeValueAsBytes(root)))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);
        verifyNoInteractions(projections);
    }

    @Test
    void eventTypeNotMatchingRoutingKey_isRejected() throws Exception {
        byte[] bytes = fixture("inpatient", "admission.started.v1.json");
        assertThatThrownBy(() -> handler.receive("admission.closed", bytes))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);
        verifyNoInteractions(projections);
    }

    @ParameterizedTest
    @ValueSource(strings = {"admissionId", "patientId", "admittedAt"})
    void missingRequiredField_isRejected(String field) throws Exception {
        var root = (ObjectNode) mapper.readTree(fixture("inpatient", "admission.started.v1.json"));
        ((ObjectNode) root.path("payload")).remove(field);
        byte[] bytes = mapper.writeValueAsBytes(root);
        assertThatThrownBy(() -> handler.receive("admission.started", bytes))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);
        verifyNoInteractions(projections);
    }

    private static byte[] fixture(String module, String fileName) throws Exception {
        return Files.readAllBytes(Path.of("../" + module + "-service/src/test/resources/contracts/" + fileName));
    }

    private static byte[] surgeryFixture(String fileName) throws Exception {
        return Files.readAllBytes(Path.of("../surgery-service/src/test/resources/contracts/surgery-outcomes-v1/" + fileName));
    }
}
