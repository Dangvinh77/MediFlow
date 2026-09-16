package com.mediflow.clinical.application.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import com.mediflow.clinical.application.dto.command.LabResultCreatedCommand;
import com.mediflow.clinical.application.port.in.AttachExternalResultUseCase;
import com.mediflow.clinical.application.port.out.ProcessedEventPort;

class ClinicalIntegrationServiceTest {

    private final AttachExternalResultUseCase attachments = mock(AttachExternalResultUseCase.class);
    private final ProcessedEventPort processedEvents = mock(ProcessedEventPort.class);
    private final ClinicalIntegrationService service =
            new ClinicalIntegrationService(attachments, processedEvents);

    @Test
    void onLabResultCreated_claimedEvent_attachesOnce() {
        LabResultCreatedCommand command = command();
        when(processedEvents.tryClaim(command.eventId(), "lab.result.created")).thenReturn(true);

        service.onLabResultCreated(command);

        InOrder order = inOrder(processedEvents, attachments);
        order.verify(processedEvents).tryClaim(command.eventId(), "lab.result.created");
        order.verify(attachments, times(1)).attachLabResult(
                command.recordId(), command.labId(), command.conclusion());
    }

    @Test
    void onLabResultCreated_unclaimedEvent_doesNotAttach() {
        LabResultCreatedCommand command = command();
        when(processedEvents.tryClaim(command.eventId(), "lab.result.created")).thenReturn(false);

        service.onLabResultCreated(command);

        verifyNoInteractions(attachments);
    }

    @Test
    void onLabResultCreated_attachmentFails_propagatesAfterClaim() {
        LabResultCreatedCommand command = command();
        when(processedEvents.tryClaim(command.eventId(), "lab.result.created")).thenReturn(true);
        doThrow(new IllegalStateException("database unavailable"))
                .when(attachments).attachLabResult(
                        command.recordId(), command.labId(), command.conclusion());

        assertThatThrownBy(() -> service.onLabResultCreated(command))
                .isInstanceOf(IllegalStateException.class);
        verify(processedEvents).tryClaim(command.eventId(), "lab.result.created");
    }

    private LabResultCreatedCommand command() {
        return new LabResultCreatedCommand(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "Normal");
    }
}
