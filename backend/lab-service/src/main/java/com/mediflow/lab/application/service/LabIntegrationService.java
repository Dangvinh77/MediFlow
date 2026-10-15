package com.mediflow.lab.application.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mediflow.lab.application.dto.command.MedicalRecordCreatedCommand;
import com.mediflow.lab.application.dto.command.PaymentCompletedCommand;
import com.mediflow.lab.application.port.in.ReactToMedicalRecordUseCase;
import com.mediflow.lab.application.port.in.ReactToPaymentUseCase;
import com.mediflow.lab.application.port.in.UpdateLabPaymentUseCase;

@Service
public class LabIntegrationService implements ReactToMedicalRecordUseCase, ReactToPaymentUseCase {

    private final UpdateLabPaymentUseCase payments;

    public LabIntegrationService(UpdateLabPaymentUseCase payments) {
        this.payments = payments;
    }

    /**
     * Records the delivery without creating a test. The canonical Clinical payload contains a
     * diagnosis summary, not an explicit lab order, so Lab must not infer an order from it.
     */
    @Override
    @Transactional
    public void onMedicalRecordCreated(MedicalRecordCreatedCommand command) {
        // Receipt-only: the live Clinical contract does not authorize Lab test creation.
    }

    @Override
    @Transactional
    public void onPaymentCompleted(PaymentCompletedCommand command) {
        command.labTestIds().forEach(payments::markPaid);
    }
}
