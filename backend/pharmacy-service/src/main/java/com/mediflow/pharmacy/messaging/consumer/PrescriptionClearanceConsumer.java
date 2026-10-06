package com.mediflow.pharmacy.messaging.consumer;

import com.mediflow.common.exception.BusinessRuleException;
import com.mediflow.pharmacy.application.port.in.ProjectPrescriptionClearanceUseCase;
import com.mediflow.pharmacy.application.port.out.PrescriptionClearanceWirePort;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;

/** ACK only after the application proxy commits a verified or durable pending proof. Never dispenses. */
public class PrescriptionClearanceConsumer {
    private final PrescriptionClearanceWirePort decoder;
    private final ProjectPrescriptionClearanceUseCase project;
    public PrescriptionClearanceConsumer(PrescriptionClearanceWirePort decoder, ProjectPrescriptionClearanceUseCase project) {
        this.decoder = decoder; this.project = project;
    }

    @RabbitListener(id = "pharmacyPrescriptionClearance", queues = "pharmacy.financial-clearance.q",
            containerFactory = "pharmacyClearanceListenerFactory")
    public void receive(Message message) {
        final java.util.Optional<com.mediflow.pharmacy.application.dto.command.PrescriptionClearanceCommand> command;
        try {
            command = decoder.decodeApplicable(message.getMessageProperties().getReceivedRoutingKey(), message.getBody());
        } catch (IllegalArgumentException invalid) {
            throw new AmqpRejectAndDontRequeueException("Invalid prescription clearance contract");
        }
        if (command.isEmpty()) return;
        try {
            project.project(command.orElseThrow());
        } catch (BusinessRuleException | IllegalArgumentException conflict) {
            throw new AmqpRejectAndDontRequeueException("Prescription clearance target or identity conflict");
        }
        // Transient infrastructure errors propagate to bounded retry, then durable DLQ.
    }
}
