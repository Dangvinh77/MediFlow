package com.mediflow.clinical.application.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mediflow.clinical.application.exception.ConsumerInboxConflictException;
import com.mediflow.clinical.application.dto.command.LabResultCreatedCommand;
import com.mediflow.clinical.application.dto.command.PrescriptionFilledCommand;
import com.mediflow.clinical.application.port.in.AttachExternalResultUseCase;
import com.mediflow.clinical.application.port.in.ReactToLabResultUseCase;
import com.mediflow.clinical.application.port.in.ReactToPrescriptionFilledUseCase;
import com.mediflow.clinical.application.port.out.MedicalRecordRepositoryPort;
import com.mediflow.clinical.domain.model.CareEpisodeType;
import com.mediflow.clinical.domain.model.MedicalRecord;

@Service
public class ClinicalIntegrationService implements ReactToLabResultUseCase, ReactToPrescriptionFilledUseCase {

    private final AttachExternalResultUseCase attachments;
    private final MedicalRecordRepositoryPort records;

    public ClinicalIntegrationService(AttachExternalResultUseCase attachments,
                                      MedicalRecordRepositoryPort records) {
        this.attachments = attachments;
        this.records = records;
    }

    @Override
    @Transactional
    public void onLabResultCreated(LabResultCreatedCommand command) {
        if (command.sourceVersion() == 1) {
            if (command.resultVersion() == null || command.resultVersion() != 1
                    || command.careEpisodeType() != CareEpisodeType.OUTPATIENT_VISIT) {
                throw new ConsumerInboxConflictException("unsupported lab result version or care episode");
            }
            MedicalRecord record = records.findByIdForUpdate(command.recordId())
                    .orElseThrow(() -> new ConsumerInboxConflictException(
                            "lab result references an unknown medical record"));
            java.util.UUID canonicalEpisodeId = record.getAppointmentId() == null
                    ? record.getRecordId() : record.getAppointmentId();
            if (!record.getPatientId().equals(command.patientId())
                    || !record.getDepartmentId().equals(command.departmentId())
                    || !canonicalEpisodeId.equals(command.careEpisodeId())) {
                throw new ConsumerInboxConflictException(
                        "lab result patient, department, or care episode does not match the medical record");
            }
        } else if (command.sourceVersion() != 0) {
            throw new ConsumerInboxConflictException("unsupported lab result contract version");
        }
        if (!attachments.attachLabResult(command.recordId(), command.labId(), command.conclusion())) {
            throw new ConsumerInboxConflictException("lab result attachment produced no new effect");
        }
    }

    @Override
    @Transactional
    public void onPrescriptionFilled(PrescriptionFilledCommand command) {
        if (!attachments.attachPrescription(command.recordId(), command.prescriptionId())) {
            throw new ConsumerInboxConflictException("prescription attachment produced no new effect");
        }
    }
}
