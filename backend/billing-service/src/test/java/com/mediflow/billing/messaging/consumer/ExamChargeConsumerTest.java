package com.mediflow.billing.messaging.consumer;

import com.mediflow.billing.application.dto.command.ExamChargeCommand;
import com.mediflow.billing.application.port.in.IssueExamChargeUseCase;
import com.mediflow.billing.application.port.out.ExamChargeWirePort;
import com.mediflow.billing.domain.exception.BillingRuleException;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ExamChargeConsumerTest {
    private final ExamChargeWirePort wire = mock(ExamChargeWirePort.class);
    private final IssueExamChargeUseCase charges = mock(IssueExamChargeUseCase.class);
    private final ExamChargeConsumer consumer = new ExamChargeConsumer(wire, charges);

    @Test
    void notApplicableTransition_isAckedWithoutIssuing() {
        var properties = new MessageProperties();
        properties.setReceivedRoutingKey("appointment.status.changed");
        var message = new Message(new byte[]{1}, properties);
        when(wire.decodeApplicable("appointment.status.changed", message.getBody())).thenReturn(Optional.empty());

        consumer.receive(message);

        verify(charges, never()).issue(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void malformedEnvelope_rejectedToDlq() {
        var properties = new MessageProperties();
        properties.setReceivedRoutingKey("appointment.status.changed");
        var message = new Message(new byte[]{1}, properties);
        when(wire.decodeApplicable("appointment.status.changed", message.getBody()))
                .thenThrow(new IllegalArgumentException("bad"));

        assertThatThrownBy(() -> consumer.receive(message)).isInstanceOf(AmqpRejectAndDontRequeueException.class);
    }

    @Test
    void conflictingIssue_rejectedToDlq() {
        var properties = new MessageProperties();
        properties.setReceivedRoutingKey("appointment.status.changed");
        var message = new Message(new byte[]{1}, properties);
        UUID appointmentId = UUID.randomUUID();
        var command = new ExamChargeCommand(UUID.randomUUID(), fingerprint(), fingerprint(), "corr-1",
                appointmentId, UUID.randomUUID(), UUID.randomUUID(), "OUTPATIENT_VISIT",
                appointmentId, "OUTPATIENT_EXAM", Instant.parse("2026-10-10T09:00:00Z"));
        when(wire.decodeApplicable("appointment.status.changed", message.getBody())).thenReturn(Optional.of(command));
        when(charges.issue(command)).thenThrow(new BillingRuleException("BILLING_EXAM_CHARGE_SOURCE_CONFLICT", "conflict"));

        assertThatThrownBy(() -> consumer.receive(message)).isInstanceOf(AmqpRejectAndDontRequeueException.class);
    }

    private static String fingerprint() {
        return "a".repeat(64);
    }
}
