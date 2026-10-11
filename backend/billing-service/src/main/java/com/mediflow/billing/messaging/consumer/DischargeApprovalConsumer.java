package com.mediflow.billing.messaging.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.billing.application.event.DischargeMedicallyApprovedEvent;
import com.mediflow.billing.application.port.in.ReactToDischargeApprovalUseCase;
import com.mediflow.common.exception.BusinessRuleException;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.dao.DuplicateKeyException;

/** Strict opt-in handler called only by the single billing.q dispatcher; no competing listener. */
public class DischargeApprovalConsumer {
    private final ObjectMapper mapper;
    private final ReactToDischargeApprovalUseCase discharges;
    public DischargeApprovalConsumer(ObjectMapper mapper, ReactToDischargeApprovalUseCase discharges) {
        this.mapper = mapper; this.discharges = discharges;
    }
    public void receive(Message message) {
        try {
            var event = mapper.readValue(message.getBody(), DischargeMedicallyApprovedEvent.class);
            discharges.onDischargeMedicallyApproved(event);
        }
        catch (java.io.IOException | IllegalArgumentException | BusinessRuleException | DuplicateKeyException invalid) {
            throw new AmqpRejectAndDontRequeueException("Invalid or conflicting Discharge approval fact");
        }
    }
}
