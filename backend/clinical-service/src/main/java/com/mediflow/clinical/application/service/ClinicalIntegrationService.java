package com.mediflow.clinical.application.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mediflow.clinical.application.dto.command.LabResultCreatedCommand;
import com.mediflow.clinical.application.port.in.AttachExternalResultUseCase;
import com.mediflow.clinical.application.port.in.ReactToLabResultUseCase;
import com.mediflow.clinical.application.port.out.ProcessedEventPort;

@Service
public class ClinicalIntegrationService implements ReactToLabResultUseCase {

    private static final String LAB_RESULT_CREATED = "lab.result.created";

    private final AttachExternalResultUseCase attachments;
    private final ProcessedEventPort processedEvents;

    public ClinicalIntegrationService(AttachExternalResultUseCase attachments,
                                      ProcessedEventPort processedEvents) {
        this.attachments = attachments;
        this.processedEvents = processedEvents;
    }

    @Override
    @Transactional
    public void onLabResultCreated(LabResultCreatedCommand command) {
        if (processedEvents.alreadyProcessed(command.eventId())) {
            return;
        }
        attachments.attachLabResult(command.recordId(), command.labId(), command.conclusion());
        processedEvents.markProcessed(command.eventId(), LAB_RESULT_CREATED);
    }
}
