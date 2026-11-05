package com.mediflow.billing.messaging.consumer;

import com.mediflow.billing.application.port.in.IssueLabTestChargeUseCase;
import com.mediflow.billing.application.port.out.LabTestChargeWirePort;
import com.mediflow.common.exception.BusinessRuleException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.dao.DuplicateKeyException;

/** Strict opt-in handler called only by the single billing.q dispatcher; no competing listener. */
@Slf4j
public class LabTestChargeConsumer {
    private final LabTestChargeWirePort wire;
    private final IssueLabTestChargeUseCase charges;
    public LabTestChargeConsumer(LabTestChargeWirePort wire, IssueLabTestChargeUseCase charges) {
        this.wire = wire; this.charges = charges;
    }
    public void receive(Message message) {
        try {
            var command = wire.decode(message.getMessageProperties().getReceivedRoutingKey(), message.getBody());
            charges.issue(command);
        }
        catch (IllegalArgumentException | BusinessRuleException | DuplicateKeyException invalid) {
            // The Lab test already exists; without the reason (e.g. an unknown price code) the
            // dead-lettered fact is undiagnosable from the Billing log alone.
            String reason = invalid instanceof BusinessRuleException rule ? rule.getCode() : invalid.getMessage();
            log.warn("Rejected lab.request.created messageId={} reason={}",
                    message.getMessageProperties().getMessageId(), reason);
            throw new AmqpRejectAndDontRequeueException("Invalid or conflicting Lab test charge fact: " + reason, invalid);
        }
    }
}
