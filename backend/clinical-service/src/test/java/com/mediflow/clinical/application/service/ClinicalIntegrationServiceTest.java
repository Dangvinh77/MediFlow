package com.mediflow.clinical.application.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.mediflow.clinical.application.dto.command.LabResultCreatedCommand;
import com.mediflow.clinical.application.dto.command.PrescriptionFilledCommand;
import com.mediflow.clinical.application.exception.ConsumerInboxConflictException;
import com.mediflow.clinical.application.port.in.AttachExternalResultUseCase;
import com.mediflow.clinical.application.port.out.MedicalRecordRepositoryPort;
import com.mediflow.clinical.domain.model.CareEpisodeType;
import com.mediflow.clinical.domain.model.MedicalRecord;

class ClinicalIntegrationServiceTest {

    private final AttachExternalResultUseCase attachments = mock(AttachExternalResultUseCase.class);
    private final MedicalRecordRepositoryPort records = mock(MedicalRecordRepositoryPort.class);
    private final ClinicalIntegrationService service = new ClinicalIntegrationService(attachments, records);

    @Test
    void onLabResultCreated_legacyV0AttachesOnce() {
        LabResultCreatedCommand command = new LabResultCreatedCommand(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "Normal");
        when(attachments.attachLabResult(command.recordId(), command.labId(), command.conclusion()))
                .thenReturn(true);

        service.onLabResultCreated(command);

        verify(attachments).attachLabResult(command.recordId(), command.labId(), command.conclusion());
    }

    @Test
    void onLabResultCreated_v1RequiresTheRecordPatientDepartmentAndEpisodeToMatch() {
        UUID recordId = UUID.randomUUID();
        UUID patientId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        UUID appointmentId = UUID.randomUUID();
        MedicalRecord record = mock(MedicalRecord.class);
        when(record.getPatientId()).thenReturn(patientId);
        when(record.getDepartmentId()).thenReturn(departmentId);
        when(record.getAppointmentId()).thenReturn(appointmentId);
        when(record.getRecordId()).thenReturn(recordId);
        when(records.findByIdForUpdate(recordId)).thenReturn(Optional.of(record));
        LabResultCreatedCommand command = new LabResultCreatedCommand(UUID.randomUUID(), recordId,
                UUID.randomUUID(), "Normal", 1, patientId, departmentId,
                CareEpisodeType.OUTPATIENT_VISIT, appointmentId, 1);
        when(attachments.attachLabResult(recordId, command.labId(), "Normal")).thenReturn(true);

        service.onLabResultCreated(command);

        verify(records).findByIdForUpdate(recordId);
        verify(attachments).attachLabResult(recordId, command.labId(), "Normal");
    }

    @Test
    void onLabResultCreated_v1WrongEpisodeIsAConflictBeforeAttachment() {
        UUID recordId = UUID.randomUUID();
        UUID appointmentId = UUID.randomUUID();
        MedicalRecord record = mock(MedicalRecord.class);
        when(record.getPatientId()).thenReturn(UUID.randomUUID());
        when(record.getDepartmentId()).thenReturn(UUID.randomUUID());
        when(record.getAppointmentId()).thenReturn(appointmentId);
        when(record.getRecordId()).thenReturn(recordId);
        when(records.findByIdForUpdate(recordId)).thenReturn(Optional.of(record));
        LabResultCreatedCommand command = new LabResultCreatedCommand(UUID.randomUUID(), recordId,
                UUID.randomUUID(), "Normal", 1, record.getPatientId(), record.getDepartmentId(),
                CareEpisodeType.OUTPATIENT_VISIT, UUID.randomUUID(), 1);

        assertThatThrownBy(() -> service.onLabResultCreated(command))
                .isInstanceOf(ConsumerInboxConflictException.class);
    }

    @Test
    void onLabResultCreated_missingMaterialEffectIsAConflict() {
        LabResultCreatedCommand command = new LabResultCreatedCommand(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "Normal");
        when(attachments.attachLabResult(command.recordId(), command.labId(), command.conclusion()))
                .thenReturn(false);

        assertThatThrownBy(() -> service.onLabResultCreated(command))
                .isInstanceOf(ConsumerInboxConflictException.class);
    }

    @Test
    void onPrescriptionFilled_requiresAnInsertedAttachment() {
        PrescriptionFilledCommand command = new PrescriptionFilledCommand(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        when(attachments.attachPrescription(command.recordId(), command.prescriptionId())).thenReturn(true);

        service.onPrescriptionFilled(command);

        verify(attachments).attachPrescription(command.recordId(), command.prescriptionId());
    }
}
