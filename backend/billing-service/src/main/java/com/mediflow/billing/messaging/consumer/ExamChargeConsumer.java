package com.mediflow.billing.messaging.consumer;

import com.mediflow.billing.application.port.in.IssueExamChargeUseCase;
import com.mediflow.billing.application.port.out.ExamChargeWirePort;
import com.mediflow.common.exception.BusinessRuleException;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.dao.DuplicateKeyException;

/** Strict opt-in handler called only by the single billing.q dispatcher; no competing listener.
 * A valid {@code appointment.status.changed} transition other than the check-in charge trigger is
 * an explicit no-op, not a reject. */
public class ExamChargeConsumer {
    private final ExamChargeWirePort wire;
    private final IssueExamChargeUseCase charges;
    public ExamChargeConsumer(ExamChargeWirePort wire, IssueExamChargeUseCase charges) {
        this.wire = wire; this.charges = charges;
    }
    public void receive(Message message) {
        final java.util.Optional<com.mediflow.billing.application.dto.command.ExamChargeCommand> command;
        try {
            command = wire.decodeApplicable(message.getMessageProperties().getReceivedRoutingKey(), message.getBody());
        } catch (IllegalArgumentException malformed) {
            throw new AmqpRejectAndDontRequeueException("Invalid Exam charge fact");
        }
        if (command.isEmpty()) return; // valid but unrelated appointment transition: no effect
        try {
            charges.issue(command.get());
        } catch (IllegalArgumentException | BusinessRuleException | DuplicateKeyException invalid) {
            throw new AmqpRejectAndDontRequeueException("Invalid or conflicting Exam charge fact");
        }
    }
}
