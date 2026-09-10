package com.mediflow.lab.messaging.consumer;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import com.mediflow.lab.application.dto.command.MedicalRecordCreatedCommand;
import com.mediflow.lab.application.port.in.ReactToMedicalRecordUseCase;
import com.mediflow.lab.messaging.consumer.payload.MedicalRecordCreatedPayload;

@Component
public class MedicalRecordCreatedConsumer {

    private final ReactToMedicalRecordUseCase useCase;

    public MedicalRecordCreatedConsumer(ReactToMedicalRecordUseCase useCase) {
        this.useCase = useCase;
    }

    @RabbitListener(queues = "${mediflow.lab.rabbit.queue:lab.q}")
    public void consume(MedicalRecordCreatedPayload payload) {
        useCase.onMedicalRecordCreated(new MedicalRecordCreatedCommand(
                payload.eventId(), payload.recordId(), payload.patientId(), payload.departmentId()));
    }
}
