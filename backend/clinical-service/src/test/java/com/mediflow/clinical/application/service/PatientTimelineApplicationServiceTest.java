package com.mediflow.clinical.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.mediflow.clinical.application.dto.authority.RecordReadDecision;
import com.mediflow.clinical.application.dto.response.TimelineEntryDTO;
import com.mediflow.clinical.application.port.out.ExternalResultRepositoryPort;
import com.mediflow.clinical.application.port.out.MedicalRecordRepositoryPort;
import com.mediflow.clinical.application.port.out.PatientTimelineReadPort;
import com.mediflow.clinical.application.port.out.PatientTimelineReadPort.TimelineRow;
import com.mediflow.clinical.domain.model.MedicalRecordStatus;
import com.mediflow.common.exception.ForbiddenOperationException;

class PatientTimelineApplicationServiceTest {
    final UUID patient = UUID.randomUUID();
    final UUID department = UUID.randomUUID();
    final UUID doctor = UUID.randomUUID();
    final PatientTimelineReadPort port = mock(PatientTimelineReadPort.class);
    final RecordReadGuard guard = mock(RecordReadGuard.class);
    final PatientTimelineApplicationService service = new PatientTimelineApplicationService(port,
            mock(MedicalRecordRepositoryPort.class), mock(ExternalResultRepositoryPort.class), guard, true);

    private TimelineRow row(String visitType, UUID previous, UUID responsible) {
        return new TimelineRow(UUID.randomUUID(), LocalDate.parse("2026-11-10"), department, doctor, responsible,
                visitType, "COMPLETED", "OTHER", previous);
    }

    private List<TimelineEntryDTO> read(TimelineRow... rows) {
        when(guard.requirePatient(patient)).thenReturn(RecordReadDecision.allow("DOCTOR"));
        when(port.page(eq(patient), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(new PatientTimelineReadPort.TimelinePage(List.of(rows), rows.length));
        return service.timeline(patient, 0, 20, null, null).content();
    }

    @Test void newestFirstWithDepartmentDoctorKindAndStatus() {
        TimelineEntryDTO entry = read(row("NEW_VISIT", null, null)).get(0);
        assertThat(entry.departmentId()).isEqualTo(department);
        assertThat(entry.doctorId()).isEqualTo(doctor);
        assertThat(entry.visitKind()).isEqualTo("NEW");
        assertThat(entry.status()).isEqualTo("COMPLETED");
        assertThat(entry.disposition()).isEqualTo("OTHER");
    }

    @Test void followUpRegistrationIsFollowUp() {
        assertThat(read(row("FOLLOW_UP", UUID.randomUUID(), null)).get(0).visitKind()).isEqualTo("FOLLOW_UP");
    }

    @Test void previousRecordWithoutFollowUpIsReturn() {
        assertThat(read(row("NEW_VISIT", UUID.randomUUID(), null)).get(0).visitKind()).isEqualTo("RETURN");
    }

    @Test void legacyRecordHasNoKind() {
        assertThat(read(row(null, null, null)).get(0).visitKind()).isNull();
    }

    @Test void responsibleDoctorShownOnlyWhenDifferent() {
        UUID other = UUID.randomUUID();
        List<TimelineEntryDTO> entries = read(row("NEW_VISIT", null, doctor), row("NEW_VISIT", null, other));
        assertThat(entries.get(0).responsibleDoctorId()).isNull();
        assertThat(entries.get(1).responsibleDoctorId()).isEqualTo(other);
    }

    @Test void filtersByDepartmentAndStatus() {
        when(guard.requirePatient(patient)).thenReturn(RecordReadDecision.allow("DOCTOR"));
        when(port.page(any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(new PatientTimelineReadPort.TimelinePage(List.of(), 0));
        service.timeline(patient, 1, 10, department, MedicalRecordStatus.OPEN);
        verify(port).page(patient, department, null, "OPEN", 1, 10);
    }

    @Test void sizeIsCappedAt50() {
        when(guard.requirePatient(patient)).thenReturn(RecordReadDecision.allow("DOCTOR"));
        when(port.page(any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(new PatientTimelineReadPort.TimelinePage(List.of(), 0));
        assertThat(service.timeline(patient, -3, 500, null, null).size()).isEqualTo(50);
        verify(port).page(patient, null, null, null, 0, 50);
    }

    @Test void nurseSeesOnlyOwnDepartment() {
        UUID ward = UUID.randomUUID();
        when(guard.requirePatient(patient)).thenReturn(RecordReadDecision.allowDepartment("NURSE", ward));
        when(port.page(any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(new PatientTimelineReadPort.TimelinePage(List.of(), 0));
        service.timeline(patient, 0, 20, null, null);
        verify(port).page(patient, null, ward, null, 0, 20);
    }

    @Test void deniedReaderGets403() {
        when(guard.requirePatient(patient)).thenThrow(new ForbiddenOperationException("RECORD_READ_DENIED", "no"));
        assertThatThrownBy(() -> service.timeline(patient, 0, 20, null, null))
                .isInstanceOf(ForbiddenOperationException.class);
        verifyNoInteractions(port);
    }
}
