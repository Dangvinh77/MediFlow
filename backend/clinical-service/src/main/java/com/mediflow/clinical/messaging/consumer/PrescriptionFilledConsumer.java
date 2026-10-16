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
                payload.eventId(), payload.recordId(), payload.prescriptionId(), payload.sourceVersion(),
                payload.patientId(), payload.departmentId(), payload.careEpisodeType(), payload.careEpisodeId(),
                payload.dispenseId()));
    }

    static void validate(PrescriptionFilledPayload payload) {
        if (payload == null) {
            throw new IllegalArgumentException("prescription.filled payload is required");
        }
        require(payload.eventId(), "eventId");
        require(payload.recordId(), "recordId");
        require(payload.prescriptionId(), "prescriptionId");
        if (payload.sourceVersion() == 1) {
            require(payload.patientId(), "patientId");
            require(payload.departmentId(), "departmentId");
            require(payload.careEpisodeId(), "careEpisodeId");
            require(payload.dispenseId(), "dispenseId");
            require(payload.filledAt(), "filledAt");
            if (!"OUTPATIENT_VISIT".equals(payload.careEpisodeType())) {
                throw new IllegalArgumentException("prescription.filled requires OUTPATIENT_VISIT");
            }
        } else if (payload.sourceVersion() != 0) {
            throw new IllegalArgumentException("Unsupported prescription.filled version");
        }
    }

    private static void require(Object value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException("prescription.filled " + fieldName + " is required");
        }
    }
}
