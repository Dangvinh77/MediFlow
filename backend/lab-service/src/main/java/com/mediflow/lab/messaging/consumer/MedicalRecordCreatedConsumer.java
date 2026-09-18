package com.mediflow.lab.messaging.consumer;

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

    public void consume(MedicalRecordCreatedPayload payload) {
        validate(payload);
        useCase.onMedicalRecordCreated(new MedicalRecordCreatedCommand(
                payload.eventId(), payload.recordId(), payload.patientId(), payload.departmentId()));
    }

    private static void validate(MedicalRecordCreatedPayload payload) {
        if (payload == null) {
            throw new IllegalArgumentException("medicalrecord.created payload is required");
        }
        require(payload.eventId(), "eventId");
        require(payload.recordId(), "recordId");
        require(payload.patientId(), "patientId");
        require(payload.departmentId(), "departmentId");
    }

    private static void require(Object value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException("medicalrecord.created " + fieldName + " is required");
        }
    }
}
