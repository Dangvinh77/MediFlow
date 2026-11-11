package com.mediflow.clinical.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.mediflow.clinical.application.dto.response.RecordDocumentDTO;
import com.mediflow.clinical.application.port.out.ExternalResultRepositoryPort;
import com.mediflow.clinical.application.port.out.ExternalResultRepositoryPort.AttachedDocument;
import com.mediflow.clinical.application.port.out.MedicalRecordRepositoryPort;
import com.mediflow.clinical.application.port.out.PatientTimelineReadPort;
import com.mediflow.clinical.domain.model.Diagnosis;
import com.mediflow.clinical.domain.model.MedicalRecord;
import com.mediflow.clinical.domain.model.RecordDisposition;
import com.mediflow.common.exception.ForbiddenOperationException;

class RecordDocumentsTest {
    static final Instant OPENED = Instant.parse("2026-11-10T02:00:00Z");
    static final Instant COMPLETED = Instant.parse("2026-11-10T04:00:00Z");

    final MedicalRecordRepositoryPort records = mock(MedicalRecordRepositoryPort.class);
    final ExternalResultRepositoryPort results = mock(ExternalResultRepositoryPort.class);
    final RecordReadGuard guard = mock(RecordReadGuard.class);
    final PatientTimelineApplicationService service = new PatientTimelineApplicationService(
            mock(PatientTimelineReadPort.class), records, results, guard, true);
    final MedicalRecord record = completed();

    private static MedicalRecord completed() {
        MedicalRecord record = MedicalRecord.openForAppointment(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                LocalDate.parse("2026-11-10"), "Sốt", UUID.randomUUID(), OPENED);
        record.addDiagnosis(Diagnosis.create("Viêm họng", null, "J02"));
        record.complete(RecordDisposition.values()[0], null, COMPLETED);
        return record;
    }

    private RecordDocumentDTO only(Instant attachedAt) {
        when(records.findById(record.getRecordId())).thenReturn(Optional.of(record));
        when(results.findByRecord(record.getRecordId())).thenReturn(List.of(
                new AttachedDocument("LAB", UUID.randomUUID(), "Bình thường", attachedAt)));
        return service.documents(record.getRecordId()).get(0);
    }

    @Test void documentAttachedAfterCompletionIsMarked() {
        RecordDocumentDTO document = only(COMPLETED.plusSeconds(60));
        assertThat(document.afterConclusion()).isTrue();
        assertThat(document.type()).isEqualTo("LAB");
        assertThat(document.summary()).isEqualTo("Bình thường");
    }

    @Test void documentBeforeCompletionIsNotMarked() {
        assertThat(only(COMPLETED.minusSeconds(60)).afterConclusion()).isFalse();
    }

    @Test void amendedLabShowsTheCurrentRevisionAndLabelsEarlierOnes() {
        UUID lab = UUID.randomUUID();
        when(records.findById(record.getRecordId())).thenReturn(Optional.of(record));
        when(results.findByRecord(record.getRecordId())).thenReturn(List.of(
                new AttachedDocument("LAB", lab, "Thiếu máu nhẹ", COMPLETED.plusSeconds(60))));
        when(results.labResultRevisions(record.getRecordId(), lab)).thenReturn(List.of(
                new ExternalResultRepositoryPort.LabResultRevision(1, "Bình thường", null, COMPLETED.minusSeconds(60)),
                new ExternalResultRepositoryPort.LabResultRevision(2, "Thiếu máu nhẹ", "Nhập nhầm HGB",
                        COMPLETED.plusSeconds(60))));

        RecordDocumentDTO document = service.documents(record.getRecordId()).get(0);

        assertThat(document.resultVersion()).isEqualTo(2);
        assertThat(document.summary()).isEqualTo("Thiếu máu nhẹ");
        assertThat(document.amendmentReason()).isEqualTo("Nhập nhầm HGB");
        assertThat(document.previousRevisions()).singleElement().satisfies(previous -> {
            assertThat(previous.resultVersion()).isEqualTo(1);
            assertThat(previous.summary()).isEqualTo("Bình thường");
            assertThat(previous.label()).isEqualTo("Đã đính chính");
        });
    }

    @Test void prescriptionsHaveNoRevisions() {
        when(records.findById(record.getRecordId())).thenReturn(Optional.of(record));
        when(results.findByRecord(record.getRecordId())).thenReturn(List.of(
                new AttachedDocument("PRESCRIPTION", UUID.randomUUID(), "Đơn thuốc", COMPLETED)));
        RecordDocumentDTO document = service.documents(record.getRecordId()).get(0);
        assertThat(document.resultVersion()).isNull();
        assertThat(document.previousRevisions()).isEmpty();
        org.mockito.Mockito.verify(results, org.mockito.Mockito.never())
                .labResultRevisions(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test void documentsNeedRecordRead() {
        when(records.findById(record.getRecordId())).thenReturn(Optional.of(record));
        when(guard.requireRecord(record)).thenThrow(new ForbiddenOperationException("RECORD_READ_DENIED", "no"));
        assertThatThrownBy(() -> service.documents(record.getRecordId())).isInstanceOf(ForbiddenOperationException.class);
        verifyNoInteractions(results);
    }
}
