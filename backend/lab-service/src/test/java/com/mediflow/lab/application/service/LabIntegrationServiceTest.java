package com.mediflow.lab.application.service;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import com.mediflow.lab.application.dto.command.MedicalRecordCreatedCommand;
import com.mediflow.lab.application.port.out.ProcessedEventPort;

class LabIntegrationServiceTest {

    private final ProcessedEventPort processedEvents = mock(ProcessedEventPort.class);
    private final LabIntegrationService service = new LabIntegrationService(processedEvents);

    @Test
    void onMedicalRecordCreated_newEvent_recordsDeliberateNoOp() {
        MedicalRecordCreatedCommand command = command();

        service.onMedicalRecordCreated(command);

        InOrder order = inOrder(processedEvents);
        order.verify(processedEvents).alreadyProcessed(command.eventId());
        order.verify(processedEvents).markProcessed(command.eventId(), "medicalrecord.created");
    }

    @Test
    void onMedicalRecordCreated_repeatedEvent_doesNotRecordAgain() {
        MedicalRecordCreatedCommand command = command();
        when(processedEvents.alreadyProcessed(command.eventId())).thenReturn(true);

        service.onMedicalRecordCreated(command);

        verify(processedEvents, never()).markProcessed(command.eventId(), "medicalrecord.created");
    }

    private MedicalRecordCreatedCommand command() {
        return new MedicalRecordCreatedCommand(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
    }
}
