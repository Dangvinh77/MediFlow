package com.mediflow.pharmacy.messaging.consumer;

import com.mediflow.common.exception.BusinessRuleException;
import com.mediflow.pharmacy.application.dto.command.AdmissionLifecycleCommand;
import com.mediflow.pharmacy.application.port.in.ProjectAdmissionLifecycleUseCase;
import com.mediflow.pharmacy.application.port.out.AdmissionLifecycleWirePort;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;

/** Return/ACK only after the application proxy commits context and delivery proof. Never dispenses. */
public class AdmissionLifecycleConsumer {
    private final AdmissionLifecycleWirePort decoder;
    private final ProjectAdmissionLifecycleUseCase projection;

    public AdmissionLifecycleConsumer(AdmissionLifecycleWirePort decoder, ProjectAdmissionLifecycleUseCase projection) {
        this.decoder = decoder;
        this.projection = projection;
    }

    @RabbitListener(id = "pharmacyAdmissionLifecycle", queues = "pharmacy.admission-lifecycle.q",
            containerFactory = "pharmacyAdmissionListenerFactory")
    public void receive(Message message) {
        final AdmissionLifecycleCommand command;
        try {
            command = decoder.decode(message.getMessageProperties().getReceivedRoutingKey(), message.getBody());
        } catch (IllegalArgumentException malformed) {
            throw new AmqpRejectAndDontRequeueException("Invalid admission lifecycle contract");
        }
        try {
            projection.project(command);
        } catch (BusinessRuleException | IllegalArgumentException conflict) {
            throw new AmqpRejectAndDontRequeueException("Admission lifecycle identity or source conflict");
        }
        // Storage/unavailability exceptions propagate to bounded retry; no payload logging.
    }
}
