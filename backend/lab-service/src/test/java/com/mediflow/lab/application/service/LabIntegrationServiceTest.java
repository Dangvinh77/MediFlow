package com.mediflow.lab.application.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.UUID;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;

import com.mediflow.lab.application.dto.command.MedicalRecordCreatedCommand;
import com.mediflow.lab.application.dto.command.PaymentCompletedCommand;
import com.mediflow.lab.application.port.in.UpdateLabPaymentUseCase;
import com.mediflow.lab.application.port.out.ProcessedEventPort;

class LabIntegrationServiceTest {

    private final ProcessedEventPort processedEvents = mock(ProcessedEventPort.class);
    private final UpdateLabPaymentUseCase payments = mock(UpdateLabPaymentUseCase.class);
    private final LabIntegrationService service = new LabIntegrationService(processedEvents, payments);

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

    @Test
    void onPaymentCompleted_claimedEvent_marksEveryExplicitLabTestPaid() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        PaymentCompletedCommand command = new PaymentCompletedCommand(
                UUID.randomUUID(), List.of(first, second));
        when(processedEvents.tryClaim(command.eventId(), "payment.completed")).thenReturn(true);

        service.onPaymentCompleted(command);

        verify(payments).markPaid(first);
        verify(payments).markPaid(second);
    }

    @Test
    void onPaymentCompleted_duplicateEvent_doesNotMarkTestsPaid() {
        PaymentCompletedCommand command = new PaymentCompletedCommand(
                UUID.randomUUID(), List.of(UUID.randomUUID()));
        when(processedEvents.tryClaim(command.eventId(), "payment.completed")).thenReturn(false);

        service.onPaymentCompleted(command);

        verifyNoMoreInteractions(payments);
    }

    private MedicalRecordCreatedCommand command() {
        return new MedicalRecordCreatedCommand(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
    }
}
