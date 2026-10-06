package com.mediflow.surgery.messaging.consumer;

import com.mediflow.surgery.application.port.in.ReceiveSurgeryAuthorityChangeUseCase;
import com.mediflow.surgery.application.port.out.SurgeryAuthorityChangeWirePort;
import com.mediflow.surgery.application.port.out.SurgeryClockPort;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SurgeryAuthorityChangeConsumerTest {
    private final SurgeryAuthorityChangeWirePort decoder = mock(SurgeryAuthorityChangeWirePort.class);
    private final ReceiveSurgeryAuthorityChangeUseCase receive = mock(ReceiveSurgeryAuthorityChangeUseCase.class);
    private final SurgeryClockPort clock = () -> Instant.parse("2026-10-05T02:01:00Z");
    private final SurgeryAuthorityChangeConsumer consumer = new SurgeryAuthorityChangeConsumer(decoder, receive, clock);

    @ParameterizedTest @EnumSource(ReceiveSurgeryAuthorityChangeUseCase.Outcome.class)
    void onlyCommittedAcceptedOutcomesAcknowledge(ReceiveSurgeryAuthorityChangeUseCase.Outcome outcome) {
        var command = mock(ReceiveSurgeryAuthorityChangeUseCase.Command.class);
        when(decoder.decode(anyString(), any(), any())).thenReturn(command); when(receive.receive(command)).thenReturn(outcome);
        if (outcome == ReceiveSurgeryAuthorityChangeUseCase.Outcome.CONFLICT || outcome == ReceiveSurgeryAuthorityChangeUseCase.Outcome.QUARANTINED) {
            assertThatThrownBy(() -> consumer.receive(message())).isInstanceOf(AmqpRejectAndDontRequeueException.class);
        } else { consumer.receive(message()); verify(receive).receive(command); }
    }
    @Test void malformedDecoderRejectsWithoutCallingApplicationOrLeakingReason() {
        when(decoder.decode(anyString(), any(), any())).thenThrow(new IllegalArgumentException("sensitive input"));
        assertThatThrownBy(() -> consumer.receive(message())).isInstanceOf(AmqpRejectAndDontRequeueException.class)
                .hasMessage("Malformed Surgery authority event").hasCause(null);
        verifyNoInteractions(receive);
    }
    @Test void missingOutcomeOrStorageFailureMustNotAck() {
        var command = mock(ReceiveSurgeryAuthorityChangeUseCase.Command.class);
        when(decoder.decode(anyString(), any(), any())).thenReturn(command);
        assertThatThrownBy(() -> consumer.receive(message())).isInstanceOf(IllegalStateException.class);
        when(receive.receive(command)).thenThrow(new IllegalStateException("database unavailable"));
        assertThatThrownBy(() -> consumer.receive(message())).isInstanceOf(IllegalStateException.class);
    }
    private Message message() {
        var properties = new MessageProperties(); properties.setReceivedRoutingKey(ReceiveSurgeryAuthorityChangeUseCase.EVENT_TYPE);
        return new Message(new byte[]{1}, properties);
    }
}
