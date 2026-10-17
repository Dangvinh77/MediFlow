package com.mediflow.notification.messaging.consumer;

import com.mediflow.notification.application.dto.command.SurgeryPaymentNoticeCommand;
import com.mediflow.notification.application.port.in.ReactToSurgeryPaymentNoticeUseCase;
import com.mediflow.notification.application.port.out.SurgeryPaymentNoticeWirePort;
import com.mediflow.notification.domain.exception.NotificationEventConflictException;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;

/** Uses the existing Billing request contract without activating deferred Surgery listeners. */
public class PrescriptionPaymentNoticeWireHandler {
    private final SurgeryPaymentNoticeWirePort wire;
    private final ReactToSurgeryPaymentNoticeUseCase notices;

    public PrescriptionPaymentNoticeWireHandler(SurgeryPaymentNoticeWirePort wire,
            ReactToSurgeryPaymentNoticeUseCase notices) {
        this.wire = wire;
        this.notices = notices;
    }

    public void receive(String key, byte[] body) {
        SurgeryPaymentNoticeCommand command;
        try {
            command = wire.decode(key, body);
            if (!"PRESCRIPTION".equals(command.purpose())) throw new IllegalArgumentException();
        } catch (IllegalArgumentException invalid) {
            throw new AmqpRejectAndDontRequeueException("Invalid outpatient prescription payment request", invalid);
        }
        try { notices.receive(command); }
        catch (NotificationEventConflictException conflict) {
            throw new AmqpRejectAndDontRequeueException("Conflicting outpatient prescription payment request", conflict);
        }
    }
}
