package com.mediflow.clinical.application.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
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
    void onLabResultCreated_newEvent_attachesThenMarksProcessed() {
        LabResultCreatedCommand command = command();

        service.onLabResultCreated(command);

        InOrder order = inOrder(attachments, processedEvents);
        order.verify(processedEvents).alreadyProcessed(command.eventId());
        order.verify(attachments).attachLabResult(
                command.recordId(), command.labId(), command.conclusion());
        order.verify(processedEvents).markProcessed(command.eventId(), "lab.result.created");
    }

    @Test
    void onLabResultCreated_repeatedEvent_hasNoAdditionalEffect() {
        LabResultCreatedCommand command = command();
        when(processedEvents.alreadyProcessed(command.eventId())).thenReturn(true);

        service.onLabResultCreated(command);

        verifyNoInteractions(attachments);
    }

    @Test
    void onLabResultCreated_attachmentFails_doesNotMarkProcessed() {
        LabResultCreatedCommand command = command();
        doThrow(new IllegalStateException("database unavailable"))
                .when(attachments).attachLabResult(
                        command.recordId(), command.labId(), command.conclusion());

        assertThatThrownBy(() -> service.onLabResultCreated(command))
                .isInstanceOf(IllegalStateException.class);
        org.mockito.Mockito.verify(processedEvents, org.mockito.Mockito.never())
                .markProcessed(command.eventId(), "lab.result.created");
    }

    private LabResultCreatedCommand command() {
        return new LabResultCreatedCommand(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "Normal");
    }
}
