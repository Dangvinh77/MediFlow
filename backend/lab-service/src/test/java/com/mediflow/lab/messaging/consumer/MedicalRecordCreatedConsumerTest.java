package com.mediflow.lab.messaging.consumer;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.mediflow.lab.application.dto.command.MedicalRecordCreatedCommand;
import com.mediflow.lab.application.port.in.ReactToMedicalRecordUseCase;
import com.mediflow.lab.messaging.consumer.payload.MedicalRecordCreatedPayload;

class MedicalRecordCreatedConsumerTest {

    private final ReactToMedicalRecordUseCase useCase = mock(ReactToMedicalRecordUseCase.class);
    private final MedicalRecordCreatedConsumer consumer = new MedicalRecordCreatedConsumer(useCase);

    @Test
    void consume_mapsCanonicalClinicalIdentifiers() {
        UUID eventId = UUID.randomUUID();
        UUID recordId = UUID.randomUUID();
        UUID patientId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        MedicalRecordCreatedPayload payload = new MedicalRecordCreatedPayload(
                eventId, Instant.now(), "correlation-test", recordId, patientId,
                UUID.randomUUID(), departmentId, "Routine examination", LocalDate.now());

        consumer.consume(payload);

        verify(useCase).onMedicalRecordCreated(
                new MedicalRecordCreatedCommand(eventId, recordId, patientId, departmentId));
    }
}
