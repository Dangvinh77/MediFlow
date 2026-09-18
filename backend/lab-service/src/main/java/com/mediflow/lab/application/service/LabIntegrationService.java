package com.mediflow.lab.application.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mediflow.lab.application.dto.command.MedicalRecordCreatedCommand;
import com.mediflow.lab.application.dto.command.PaymentCompletedCommand;
import com.mediflow.lab.application.port.in.ReactToMedicalRecordUseCase;
import com.mediflow.lab.application.port.in.ReactToPaymentUseCase;
import com.mediflow.lab.application.port.in.UpdateLabPaymentUseCase;
import com.mediflow.lab.application.port.out.ProcessedEventPort;

@Service
public class LabIntegrationService implements ReactToMedicalRecordUseCase, ReactToPaymentUseCase {

    private static final String MEDICAL_RECORD_CREATED = "medicalrecord.created";
    private static final String PAYMENT_COMPLETED = "payment.completed";

    private final ProcessedEventPort processedEvents;
    private final UpdateLabPaymentUseCase payments;

    public LabIntegrationService(ProcessedEventPort processedEvents, UpdateLabPaymentUseCase payments) {
        this.processedEvents = processedEvents;
        this.payments = payments;
    }

    /**
     * Records the delivery without creating a test. The canonical Clinical payload contains a
     * diagnosis summary, not an explicit lab order, so Lab must not infer an order from it.
     */
    @Override
    @Transactional
    public void onMedicalRecordCreated(MedicalRecordCreatedCommand command) {
        if (!processedEvents.tryClaim(command.eventId(), MEDICAL_RECORD_CREATED)) {
            return;
        }
    }

    @Override
    @Transactional
    public void onPaymentCompleted(PaymentCompletedCommand command) {
        if (!processedEvents.tryClaim(command.eventId(), PAYMENT_COMPLETED)) {
            return;
        }
        command.labTestIds().forEach(payments::markPaid);
    }
}
