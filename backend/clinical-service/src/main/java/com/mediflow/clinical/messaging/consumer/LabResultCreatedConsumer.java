package com.mediflow.clinical.messaging.consumer;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import com.mediflow.clinical.application.dto.command.LabResultCreatedCommand;
import com.mediflow.clinical.application.port.in.ReactToLabResultUseCase;
import com.mediflow.clinical.messaging.consumer.payload.LabResultCreatedPayload;

@Component
public class LabResultCreatedConsumer {

    private final ReactToLabResultUseCase useCase;

    public LabResultCreatedConsumer(ReactToLabResultUseCase useCase) {
        this.useCase = useCase;
    }

    @RabbitListener(queues = "${mediflow.clinical.rabbit.queue:clinical.q}")
    public void consume(LabResultCreatedPayload payload) {
        validate(payload);
        useCase.onLabResultCreated(new LabResultCreatedCommand(
                payload.eventId(), payload.recordId(), payload.labId(), payload.conclusion()));
    }

    private static void validate(LabResultCreatedPayload payload) {
        if (payload == null) {
            throw new IllegalArgumentException("lab.result.created payload is required");
        }
        require(payload.eventId(), "eventId");
        require(payload.recordId(), "recordId");
        require(payload.labId(), "labId");
    }

    private static void require(Object value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException("lab.result.created " + fieldName + " is required");
        }
    }
}
