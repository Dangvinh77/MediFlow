package com.mediflow.billing.messaging.consumer;

import com.mediflow.billing.application.port.in.IssueAdmissionDepositRequestUseCase;
import com.mediflow.billing.application.port.out.AdmissionDepositRequestWirePort;
import com.mediflow.common.exception.BusinessRuleException;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.dao.DuplicateKeyException;

/** Strict opt-in handler called only by the single billing.q dispatcher; no competing listener. */
public class AdmissionDepositRequestConsumer {
    private final AdmissionDepositRequestWirePort wire;
    private final IssueAdmissionDepositRequestUseCase requests;
    public AdmissionDepositRequestConsumer(AdmissionDepositRequestWirePort wire, IssueAdmissionDepositRequestUseCase requests) {
        this.wire = wire; this.requests = requests;
    }
    public void receive(Message message) {
        try {
            var command = wire.decode(message.getMessageProperties().getReceivedRoutingKey(), message.getBody());
            requests.issue(command);
        }
        catch (IllegalArgumentException | BusinessRuleException | DuplicateKeyException invalid) {
            throw new AmqpRejectAndDontRequeueException("Invalid or conflicting Admission deposit request fact");
        }
    }
}
