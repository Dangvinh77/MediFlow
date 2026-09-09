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
        useCase.onLabResultCreated(new LabResultCreatedCommand(
                payload.eventId(), payload.recordId(), payload.labId(), payload.conclusion()));
    }
}
