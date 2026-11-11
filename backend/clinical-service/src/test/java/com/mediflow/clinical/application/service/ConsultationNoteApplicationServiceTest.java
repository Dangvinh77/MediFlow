package com.mediflow.clinical.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.mediflow.clinical.application.dto.command.ActorIdentity;
import com.mediflow.clinical.application.dto.request.ConsultationNoteRequest;
import com.mediflow.clinical.application.port.out.ConsultationNoteRepositoryPort;
import com.mediflow.clinical.application.port.out.CurrentClinicalActorPort;
import com.mediflow.clinical.application.port.out.IdempotentCommandPort;
import com.mediflow.clinical.application.port.out.MedicalRecordRepositoryPort;
import com.mediflow.clinical.domain.model.ConsultationNote;
import com.mediflow.clinical.domain.model.MedicalRecord;
import com.mediflow.clinical.domain.model.RecordClosureReason;
import com.mediflow.common.exception.ForbiddenOperationException;

class ConsultationNoteApplicationServiceTest {
    static final Instant NOW = Instant.parse("2026-11-10T03:00:00Z");
    final UUID consultant = UUID.randomUUID();
    final ConsultationNoteRepositoryPort notes = mock(ConsultationNoteRepositoryPort.class);
    final MedicalRecordRepositoryPort records = mock(MedicalRecordRepositoryPort.class);
    final CurrentClinicalActorPort actors = mock(CurrentClinicalActorPort.class);
    final RecordReadGuard guard = mock(RecordReadGuard.class);
    final IdempotentCommandPort idempotency = mock(IdempotentCommandPort.class);
    final ConsultationNoteApplicationService service = new ConsultationNoteApplicationService(notes, records, actors,
            guard, true, idempotency, Clock.fixed(NOW, ZoneId.of("Asia/Ho_Chi_Minh")));
    final MedicalRecord record = MedicalRecord.openForAppointment(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
            LocalDate.parse("2026-11-10"), "Đau ngực", UUID.randomUUID(), NOW);

    @BeforeEach void setUp() {
        as(consultant, "DOCTOR");
        lenient().when(records.findById(record.getRecordId())).thenReturn(Optional.of(record));
        lenient().when(records.findByIdForUpdate(record.getRecordId())).thenReturn(Optional.of(record));
        lenient().when(idempotency.execute(anyString(), anyString(), anyString(), anyString(), anyString(), any(), anyInt(),
                any(), any())).thenAnswer(call -> new IdempotentCommandPort.Result<>(
                        ((Supplier<?>) call.getArgument(8)).get(), call.getArgument(6), null, false));
    }

    private void as(UUID staffId, String role) {
        lenient().when(actors.current()).thenReturn(new ActorIdentity(UUID.randomUUID(), staffId, null, role));
    }

    @Test void doctorWhoCanReadAddsNote() {
        var result = service.add(record.getRecordId(), new ConsultationNoteRequest("Nên làm điện tim"), "k");
        assertThat(result.status()).isEqualTo(201);
        verify(guard).requireRecord(record);
        verify(notes).insert(any());
    }

    @Test void doctorWhoCannotReadIsRefused() {
        when(guard.requireRecord(record)).thenThrow(new ForbiddenOperationException("RECORD_READ_DENIED", "no"));
        assertThatThrownBy(() -> service.add(record.getRecordId(), new ConsultationNoteRequest("Ý kiến"), "k"))
                .isInstanceOf(ForbiddenOperationException.class);
        verify(notes, never()).insert(any());
    }

    @Test void noteKeepsRealAuthor() {
        service.add(record.getRecordId(), new ConsultationNoteRequest("Ý kiến"), "k");
        ArgumentCaptor<ConsultationNote> saved = ArgumentCaptor.forClass(ConsultationNote.class);
        verify(notes).insert(saved.capture());
        assertThat(saved.getValue().getAuthorStaffId()).isEqualTo(consultant).isNotEqualTo(record.getDoctorId());
        assertThat(saved.getValue().getCreatedAt()).isEqualTo(NOW);
    }

    @Test void noteIsNeverEdited() {
        assertThat(Arrays.stream(ConsultationNoteRepositoryPort.class.getDeclaredMethods()).map(Method::getName))
                .containsExactlyInAnyOrder("insert", "findByRecord");
    }

    @Test void closedRecordRefusesNotes() {
        record.closeWithoutConclusion(RecordClosureReason.PATIENT_LEFT, "Tự về", record.getDoctorId(), NOW);
        assertThatThrownBy(() -> service.add(record.getRecordId(), new ConsultationNoteRequest("Ý kiến"), "k"))
                .extracting("code").isEqualTo("CLINICAL_INVALID_STATUS_TRANSITION");
    }

    @Test void opinionRequiredMax4000() {
        assertThatThrownBy(() -> service.add(record.getRecordId(), new ConsultationNoteRequest(" "), "k"))
                .extracting("code").isEqualTo("CONSULTATION_NOTE_INVALID");
        assertThatThrownBy(() -> service.add(record.getRecordId(), new ConsultationNoteRequest("x".repeat(4001)), "k"))
                .extracting("code").isEqualTo("CONSULTATION_NOTE_INVALID");
        assertThat(service.add(record.getRecordId(), new ConsultationNoteRequest("x".repeat(4000)), "k").status()).isEqualTo(201);
    }

    @Test void nurseCannotAdd() {
        as(UUID.randomUUID(), "NURSE");
        assertThatThrownBy(() -> service.add(record.getRecordId(), new ConsultationNoteRequest("Ý kiến"), "k"))
                .isInstanceOf(ForbiddenOperationException.class);
        verify(notes, never()).insert(any());
    }

    @Test void notesOrderedOldestFirst() {
        ConsultationNote first = ConsultationNote.write(record.getRecordId(), consultant, "Một", NOW.minusSeconds(60));
        ConsultationNote second = ConsultationNote.write(record.getRecordId(), UUID.randomUUID(), "Hai", NOW);
        when(notes.findByRecord(record.getRecordId())).thenReturn(List.of(first, second));
        assertThat(service.list(record.getRecordId())).extracting("opinion").containsExactly("Một", "Hai");
        verify(guard).requireRecord(record);
    }

    @Test void noteDoesNotChangeDiagnosis() {
        service.add(record.getRecordId(), new ConsultationNoteRequest("Nghĩ tới viêm phổi"), "k");
        verify(records, never()).save(any());
        assertThat(record.getDiagnoses()).isEmpty();
    }
}
