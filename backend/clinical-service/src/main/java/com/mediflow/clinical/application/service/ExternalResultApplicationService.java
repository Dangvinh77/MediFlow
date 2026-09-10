package com.mediflow.clinical.application.service;

import java.util.Objects;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mediflow.clinical.application.port.in.AttachExternalResultUseCase;
import com.mediflow.clinical.application.port.out.ExternalResultRepositoryPort;
import com.mediflow.clinical.application.port.out.MedicalRecordRepositoryPort;
import com.mediflow.clinical.domain.exception.MedicalRecordNotFoundException;
import com.mediflow.clinical.domain.model.ExternalResultType;

@Service
@Transactional
public class ExternalResultApplicationService implements AttachExternalResultUseCase {

    private final MedicalRecordRepositoryPort records;
    private final ExternalResultRepositoryPort externalResults;

    public ExternalResultApplicationService(MedicalRecordRepositoryPort records,
                                            ExternalResultRepositoryPort externalResults) {
        this.records = records;
        this.externalResults = externalResults;
    }

    @Override
    public void attachLabResult(UUID recordId, UUID labTestId, String conclusion) {
        attach(recordId, ExternalResultType.LAB, labTestId, conclusion);
    }

    @Override
    public void attachPrescription(UUID recordId, UUID prescriptionId) {
        attach(recordId, ExternalResultType.PRESCRIPTION, prescriptionId, null);
    }

    private void attach(UUID recordId, ExternalResultType type, UUID referenceId, String summary) {
        Objects.requireNonNull(recordId, "recordId must not be null");
        Objects.requireNonNull(referenceId, "referenceId must not be null");
        records.findById(recordId).orElseThrow(() ->
                new MedicalRecordNotFoundException("Medical record not found: " + recordId));
        externalResults.attach(recordId, type, referenceId, summary);
    }
}
