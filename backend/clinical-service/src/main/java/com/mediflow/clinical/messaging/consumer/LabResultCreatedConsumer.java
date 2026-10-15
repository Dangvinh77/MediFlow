package com.mediflow.clinical.messaging.consumer;

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

    public void consume(LabResultCreatedPayload payload) {
        validate(payload);
        useCase.onLabResultCreated(new LabResultCreatedCommand(
                payload.eventId(), payload.recordId(), payload.labId(), payload.conclusion(),
                payload.sourceVersion(), payload.patientId(), payload.departmentId(),
                payload.careEpisodeType() == null ? null
                        : com.mediflow.clinical.domain.model.CareEpisodeType.valueOf(payload.careEpisodeType()),
                payload.careEpisodeId(), payload.resultVersion()));
    }

    static void validate(LabResultCreatedPayload payload) {
        if (payload == null) {
            throw new IllegalArgumentException("lab.result.created payload is required");
        }
        require(payload.eventId(), "eventId");
        require(payload.recordId(), "recordId");
        require(payload.labId(), "labId");
        if (payload.sourceVersion() == 1) {
            require(payload.patientId(), "patientId");
            require(payload.departmentId(), "departmentId");
            require(payload.careEpisodeType(), "careEpisodeType");
            require(payload.careEpisodeId(), "careEpisodeId");
            if (!"OUTPATIENT_VISIT".equals(payload.careEpisodeType())) {
                throw new IllegalArgumentException("lab.result.created careEpisodeType is unsupported");
            }
            if (payload.resultVersion() == null || payload.resultVersion() != 1) {
                throw new IllegalArgumentException("lab.result.created resultVersion must be 1");
            }
        } else if (payload.sourceVersion() != 0) {
            throw new IllegalArgumentException("lab.result.created source version is unsupported");
        }
    }

    private static void require(Object value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException("lab.result.created " + fieldName + " is required");
        }
    }
}
