package com.mediflow.billing.messaging.consumer;

import com.mediflow.billing.application.port.in.IssueSurgeryChargeUseCase;
import com.mediflow.billing.application.port.out.SurgeryChargeWirePort;
import com.mediflow.common.exception.BusinessRuleException;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.dao.DuplicateKeyException;

/** Strict opt-in handler called only by the single billing.q dispatcher; no competing listener. */
public class SurgeryChargeConsumer {
    private final SurgeryChargeWirePort wire;
    private final IssueSurgeryChargeUseCase charges;
    private final com.mediflow.billing.application.port.in.ProcessSurgeryCancellationUseCase cancellations;
    public SurgeryChargeConsumer(SurgeryChargeWirePort wire, IssueSurgeryChargeUseCase charges) {
        this(wire, charges, null);
    }
    public SurgeryChargeConsumer(SurgeryChargeWirePort wire, IssueSurgeryChargeUseCase charges,
            com.mediflow.billing.application.port.in.ProcessSurgeryCancellationUseCase cancellations) {
        this.wire = wire; this.charges = charges; this.cancellations = cancellations;
    }
    public void receive(Message message) {
        try {
            var command = wire.decode(message.getMessageProperties().getReceivedRoutingKey(), message.getBody());
            charges.issue(command);
            // Issuance has committed. Recover even on exact replay; no in-memory arrival callback is required.
            if (cancellations != null) cancellations.recover(command.surgeryCaseId());
        }
        catch (IllegalArgumentException | BusinessRuleException | DuplicateKeyException invalid) {
            throw new AmqpRejectAndDontRequeueException("Invalid or conflicting Surgery charge fact");
        }
    }
}
