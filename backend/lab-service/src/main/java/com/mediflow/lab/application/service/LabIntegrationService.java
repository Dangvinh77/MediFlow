package com.mediflow.lab.application.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mediflow.lab.application.dto.command.MedicalRecordCreatedCommand;
import com.mediflow.lab.application.port.in.ReactToMedicalRecordUseCase;
import com.mediflow.lab.application.port.out.ProcessedEventPort;

@Service
public class LabIntegrationService implements ReactToMedicalRecordUseCase {

    private static final String MEDICAL_RECORD_CREATED = "medicalrecord.created";

    private final ProcessedEventPort processedEvents;

    public LabIntegrationService(ProcessedEventPort processedEvents) {
        this.processedEvents = processedEvents;
    }

    /**
     * Records the delivery without creating a test. The canonical Clinical payload contains a
     * diagnosis summary, not an explicit lab order, so Lab must not infer an order from it.
     */
    @Override
    @Transactional
    public void onMedicalRecordCreated(MedicalRecordCreatedCommand command) {
        if (processedEvents.alreadyProcessed(command.eventId())) {
            return;
        }
        processedEvents.markProcessed(command.eventId(), MEDICAL_RECORD_CREATED);
    }
}
