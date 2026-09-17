package com.mediflow.lab.application.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;

import com.mediflow.lab.application.dto.command.MedicalRecordCreatedCommand;
import com.mediflow.lab.application.port.out.ProcessedEventPort;

class LabIntegrationServiceTest {

    private final ProcessedEventPort processedEvents = mock(ProcessedEventPort.class);
    private final LabIntegrationService service = new LabIntegrationService(processedEvents);

    @Test
    void onMedicalRecordCreated_claimedEvent_recordsDeliberateNoOp() {
        MedicalRecordCreatedCommand command = command();
        when(processedEvents.tryClaim(command.eventId(), "medicalrecord.created")).thenReturn(true);

        service.onMedicalRecordCreated(command);

        verify(processedEvents).tryClaim(command.eventId(), "medicalrecord.created");
        verifyNoMoreInteractions(processedEvents);
    }

    @Test
    void onMedicalRecordCreated_duplicateEvent_returnsWithoutEffect() {
        MedicalRecordCreatedCommand command = command();
        when(processedEvents.tryClaim(command.eventId(), "medicalrecord.created")).thenReturn(false);

        service.onMedicalRecordCreated(command);

        verify(processedEvents).tryClaim(command.eventId(), "medicalrecord.created");
        verifyNoMoreInteractions(processedEvents);
    }

    @Test
    void onMedicalRecordCreated_databaseError_propagates() {
        MedicalRecordCreatedCommand command = command();
        RuntimeException failure = new DataAccessResourceFailureException("database unavailable");
        when(processedEvents.tryClaim(command.eventId(), "medicalrecord.created")).thenThrow(failure);

        assertThatThrownBy(() -> service.onMedicalRecordCreated(command))
                .isSameAs(failure);

        verify(processedEvents).tryClaim(command.eventId(), "medicalrecord.created");
        verifyNoMoreInteractions(processedEvents);
    }

    private MedicalRecordCreatedCommand command() {
        return new MedicalRecordCreatedCommand(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
    }
}
