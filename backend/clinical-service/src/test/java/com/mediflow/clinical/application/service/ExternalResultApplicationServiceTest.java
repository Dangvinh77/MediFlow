package com.mediflow.clinical.application.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.mediflow.clinical.application.port.out.ExternalResultRepositoryPort;
import com.mediflow.clinical.application.port.out.MedicalRecordRepositoryPort;
import com.mediflow.clinical.domain.exception.MedicalRecordNotFoundException;
import com.mediflow.clinical.domain.model.ExternalResultType;
import com.mediflow.clinical.domain.model.MedicalRecord;

class ExternalResultApplicationServiceTest {

    private final MedicalRecordRepositoryPort records = mock(MedicalRecordRepositoryPort.class);
    private final ExternalResultRepositoryPort externalResults = mock(ExternalResultRepositoryPort.class);
    private final ExternalResultApplicationService service =
            new ExternalResultApplicationService(records, externalResults);

    @Test
    void attachLabResult_existingRecord_storesSeparateLabAttachment() {
        UUID recordId = UUID.randomUUID();
        UUID labId = UUID.randomUUID();
        when(records.findById(recordId)).thenReturn(Optional.of(mock(MedicalRecord.class)));

        service.attachLabResult(recordId, labId, "No abnormality detected");

        verify(externalResults).attach(
                recordId, ExternalResultType.LAB, labId, "No abnormality detected");
    }

    @Test
    void attachPrescription_existingRecord_storesReferenceWithoutChangingSymptoms() {
        UUID recordId = UUID.randomUUID();
        UUID prescriptionId = UUID.randomUUID();
        when(records.findById(recordId)).thenReturn(Optional.of(mock(MedicalRecord.class)));

        service.attachPrescription(recordId, prescriptionId);

        verify(externalResults).attach(
                recordId, ExternalResultType.PRESCRIPTION, prescriptionId, null);
    }

    @Test
    void attachLabResult_unknownRecord_rejectsBeforeInsert() {
        UUID recordId = UUID.randomUUID();
        when(records.findById(recordId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.attachLabResult(
                recordId, UUID.randomUUID(), "Conclusion"))
                .isInstanceOf(MedicalRecordNotFoundException.class);
        verifyNoInteractions(externalResults);
    }
}
