package com.mediflow.clinical.messaging.consumer;

import org.springframework.stereotype.Component;

import com.mediflow.clinical.application.dto.command.PrescriptionFilledCommand;
import com.mediflow.clinical.application.port.in.ReactToPrescriptionFilledUseCase;
import com.mediflow.clinical.messaging.consumer.payload.PrescriptionFilledPayload;

/** Maps the Pharmacy wire contract to the Clinical application boundary. */
@Component
public class PrescriptionFilledConsumer {

    private final ReactToPrescriptionFilledUseCase useCase;

    public PrescriptionFilledConsumer(ReactToPrescriptionFilledUseCase useCase) {
        this.useCase = useCase;
    }

    public void consume(PrescriptionFilledPayload payload) {
        validate(payload);
        useCase.onPrescriptionFilled(new PrescriptionFilledCommand(
                payload.eventId(), payload.recordId(), payload.prescriptionId()));
    }

    private static void validate(PrescriptionFilledPayload payload) {
        if (payload == null) {
            throw new IllegalArgumentException("prescription.filled payload is required");
        }
        require(payload.eventId(), "eventId");
        require(payload.recordId(), "recordId");
        require(payload.prescriptionId(), "prescriptionId");
    }

    private static void require(Object value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException("prescription.filled " + fieldName + " is required");
        }
    }
}
