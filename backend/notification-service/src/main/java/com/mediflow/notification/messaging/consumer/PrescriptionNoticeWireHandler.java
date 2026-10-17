package com.mediflow.notification.messaging.consumer;

import com.mediflow.notification.application.port.in.ReactToPrescriptionNoticeUseCase;
import com.mediflow.notification.application.port.out.PrescriptionNoticeWirePort;
import com.mediflow.notification.domain.exception.NotificationEventConflictException;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;

/** Called by the existing notification.q reader, never a competing listener. */
public class PrescriptionNoticeWireHandler {
    private final PrescriptionNoticeWirePort wire;
    private final ReactToPrescriptionNoticeUseCase notices;

    public PrescriptionNoticeWireHandler(PrescriptionNoticeWirePort wire, ReactToPrescriptionNoticeUseCase notices) {
        this.wire = wire;
        this.notices = notices;
    }

    public void receive(String key, byte[] body) {
        com.mediflow.notification.application.dto.command.PrescriptionNoticeCommand command;
        try { command = wire.decode(key, body); }
        catch (IllegalArgumentException invalid) {
            throw new AmqpRejectAndDontRequeueException("Invalid outpatient prescription notice", invalid);
        }
        try { notices.receive(command); }
        catch (NotificationEventConflictException conflict) {
            throw new AmqpRejectAndDontRequeueException("Conflicting outpatient prescription notice", conflict);
        }
    }
}
