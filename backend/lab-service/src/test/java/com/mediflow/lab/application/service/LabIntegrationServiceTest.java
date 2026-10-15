package com.mediflow.lab.application.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;

import com.mediflow.lab.application.dto.command.MedicalRecordCreatedCommand;
import com.mediflow.lab.application.dto.command.PaymentCompletedCommand;
import com.mediflow.lab.application.port.in.UpdateLabPaymentUseCase;

class LabIntegrationServiceTest {

    private final UpdateLabPaymentUseCase payments = mock(UpdateLabPaymentUseCase.class);
    private final LabIntegrationService service = new LabIntegrationService(payments);

    @Test
    void onMedicalRecordCreated_isAnAuthorizedReceiptOnlyNoOp() {
        service.onMedicalRecordCreated(new MedicalRecordCreatedCommand(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()));

        verifyNoInteractions(payments);
    }

    @Test
    void onPaymentCompleted_marksEveryExplicitLabTestPaid() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();

        service.onPaymentCompleted(new PaymentCompletedCommand(UUID.randomUUID(), List.of(first, second)));

        verify(payments).markPaid(first);
        verify(payments).markPaid(second);
    }

    @Test
    void onPaymentCompleted_databaseFailurePropagatesForAtomicRollback() {
        PaymentCompletedCommand command = new PaymentCompletedCommand(
                UUID.randomUUID(), List.of(UUID.randomUUID()));
        RuntimeException failure = new DataAccessResourceFailureException("database unavailable");
        org.mockito.Mockito.doThrow(failure).when(payments).markPaid(command.labTestIds().get(0));

        assertThatThrownBy(() -> service.onPaymentCompleted(command)).isSameAs(failure);
    }
}
