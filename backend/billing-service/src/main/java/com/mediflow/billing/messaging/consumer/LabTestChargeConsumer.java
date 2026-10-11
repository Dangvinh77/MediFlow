package com.mediflow.billing.messaging.consumer;

import com.mediflow.billing.application.port.in.IssueLabTestChargeUseCase;
import com.mediflow.billing.application.port.out.LabTestChargeWirePort;
import com.mediflow.common.exception.BusinessRuleException;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.dao.DuplicateKeyException;

/** Strict opt-in handler called only by the single billing.q dispatcher; no competing listener. */
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
            throw new AmqpRejectAndDontRequeueException("Invalid or conflicting Lab test charge fact");
        }
    }
}
