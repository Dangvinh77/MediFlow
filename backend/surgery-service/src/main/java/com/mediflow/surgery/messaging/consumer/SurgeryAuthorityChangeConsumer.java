package com.mediflow.surgery.messaging.consumer;

import com.mediflow.surgery.application.port.in.ReceiveSurgeryAuthorityChangeUseCase;
import com.mediflow.surgery.application.port.out.SurgeryAuthorityChangeWirePort;
import com.mediflow.surgery.application.port.out.SurgeryClockPort;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;

/** ACK follows committed inbox/evidence/jobs, not completion of every case invalidation. */
public class SurgeryAuthorityChangeConsumer {
    public static final String QUEUE = "surgery.organization-authority.q";
    private final SurgeryAuthorityChangeWirePort decoder;
    private final ReceiveSurgeryAuthorityChangeUseCase receive;
    private final SurgeryClockPort clock;
    public SurgeryAuthorityChangeConsumer(SurgeryAuthorityChangeWirePort decoder, ReceiveSurgeryAuthorityChangeUseCase receive, SurgeryClockPort clock) {
        this.decoder = decoder; this.receive = receive; this.clock = clock;
    }
    @RabbitListener(queues = QUEUE, containerFactory = "surgeryAuthorityListenerFactory")
    public void receive(Message message) {
        final ReceiveSurgeryAuthorityChangeUseCase.Command command;
        try {
            command = decoder.decode(message.getMessageProperties().getReceivedRoutingKey(), message.getBody(), clock.now());
        } catch (IllegalArgumentException invalid) {
            throw new AmqpRejectAndDontRequeueException("Malformed Surgery authority event");
        }
        var outcome = receive.receive(command);
        if (outcome == null) throw new IllegalStateException("Authority intake did not return a durable outcome");
        if (outcome == ReceiveSurgeryAuthorityChangeUseCase.Outcome.CONFLICT || outcome == ReceiveSurgeryAuthorityChangeUseCase.Outcome.QUARANTINED) {
            throw new AmqpRejectAndDontRequeueException("Conflicting Surgery authority event");
        }
    }
}
