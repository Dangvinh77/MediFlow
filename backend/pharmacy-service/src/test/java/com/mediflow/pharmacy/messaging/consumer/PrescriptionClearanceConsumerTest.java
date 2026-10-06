package com.mediflow.pharmacy.messaging.consumer;

import com.mediflow.pharmacy.application.port.in.ProjectPrescriptionClearanceUseCase;
import com.mediflow.pharmacy.application.port.out.PrescriptionClearanceWirePort;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import java.util.Optional;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;
import static com.mediflow.pharmacy.support.ClearanceTestFixtures.grant;

class PrescriptionClearanceConsumerTest {
    private final PrescriptionClearanceWirePort decoder = mock(PrescriptionClearanceWirePort.class);
    private final ProjectPrescriptionClearanceUseCase project = mock(ProjectPrescriptionClearanceUseCase.class);
    private final PrescriptionClearanceConsumer consumer = new PrescriptionClearanceConsumer(decoder, project);
    private final byte[] bytes = {1};

    @Test void receive_validApplicable_commitsProjectionBeforeReturningAck() {
        var command = command();
        when(decoder.decodeApplicable("financial.clearance.granted", bytes)).thenReturn(Optional.of(command));
        consumer.receive(message());
        verify(project).project(command);
        verifyNoMoreInteractions(project);
    }
    @Test void receive_validOtherPurpose_neverInvokesApplication() {
        when(decoder.decodeApplicable("financial.clearance.granted", bytes)).thenReturn(Optional.empty());
        consumer.receive(message());
        verifyNoInteractions(project);
    }
    @Test void receive_invalidContract_rejectsWithoutEffectOrPayloadLeak() {
        when(decoder.decodeApplicable(any(), any())).thenThrow(new IllegalArgumentException("private raw payload"));
        assertThatThrownBy(() -> consumer.receive(message())).isInstanceOf(AmqpRejectAndDontRequeueException.class)
                .hasMessage("Invalid prescription clearance contract").hasNoCause();
        verifyNoInteractions(project);
    }
    @Test void receive_targetConflict_rejectsButInfrastructureOutageRemainsRetryable() {
        var command = command();
        when(decoder.decodeApplicable(any(), any())).thenReturn(Optional.of(command));
        doThrow(new com.mediflow.common.exception.BusinessRuleException("CONFLICT", "private details"))
                .when(project).project(command);
        assertThatThrownBy(() -> consumer.receive(message())).isInstanceOf(AmqpRejectAndDontRequeueException.class)
                .hasMessage("Prescription clearance target or identity conflict").hasNoCause();
        doThrow(new org.springframework.dao.DataAccessResourceFailureException("DB unavailable"))
                .when(project).project(command);
        assertThatThrownBy(() -> consumer.receive(message()))
                .isInstanceOf(org.springframework.dao.DataAccessResourceFailureException.class);
    }
    private com.mediflow.pharmacy.application.dto.command.PrescriptionClearanceCommand command() {
        return new com.mediflow.pharmacy.application.dto.command.PrescriptionClearanceCommand(
                java.util.UUID.randomUUID(), "a".repeat(64), grant());
    }
    private Message message() {
        var properties = new MessageProperties();
        properties.setReceivedRoutingKey("financial.clearance.granted");
        return new Message(bytes, properties);
    }
}
