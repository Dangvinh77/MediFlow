package com.mediflow.surgery.messaging.consumer;

import com.mediflow.surgery.application.port.in.ReactToSurgeryClearanceUseCase;
import com.mediflow.surgery.application.port.out.SurgeryClockPort;
import com.mediflow.surgery.application.port.out.SurgeryClearanceWirePort;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;

/** AUTO ack follows the transactional in-port; durable PENDING is retried independently. */
public class SurgeryClearanceConsumer {
    public static final String QUEUE = "surgery.financial-clearance.q";
    private final SurgeryClearanceWirePort decoder;
    private final ReactToSurgeryClearanceUseCase clearance;
    private final SurgeryClockPort clock;
    public SurgeryClearanceConsumer(SurgeryClearanceWirePort decoder,
            ReactToSurgeryClearanceUseCase clearance, SurgeryClockPort clock) {
        this.decoder = decoder; this.clearance = clearance; this.clock = clock;
    }

    @RabbitListener(queues = QUEUE, containerFactory = "surgeryClearanceListenerFactory")
    public void receive(Message message) {
        final java.util.Optional<ReactToSurgeryClearanceUseCase.Command> command;
        try {
            command = decoder.decodeApplicable(message.getMessageProperties().getReceivedRoutingKey(),
                    message.getBody(),clock.now());
        } catch (IllegalArgumentException malformed) {
            throw new AmqpRejectAndDontRequeueException("Malformed or unsupported Surgery clearance");
        }
        if (command.isEmpty()) return; // Valid but unrelated purpose: no Surgery effect.
        var outcome = clearance.receive(command.get());
        if (outcome == null) throw new IllegalStateException("Clearance handler did not provide a durable outcome");
        if (outcome == ReactToSurgeryClearanceUseCase.Outcome.CONFLICT
                || outcome == ReactToSurgeryClearanceUseCase.Outcome.QUARANTINED) {
            throw new AmqpRejectAndDontRequeueException("Surgery clearance quarantined");
        }
    }
}
