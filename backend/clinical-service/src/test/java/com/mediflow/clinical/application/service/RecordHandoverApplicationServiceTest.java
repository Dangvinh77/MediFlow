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

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.mediflow.clinical.application.dto.command.ActorIdentity;
import com.mediflow.clinical.application.dto.request.RequestHandoverRequest;
import com.mediflow.clinical.application.port.out.CurrentClinicalActorPort;
import com.mediflow.clinical.application.port.out.IdempotentCommandPort;
import com.mediflow.clinical.application.port.out.MedicalRecordRepositoryPort;
import com.mediflow.clinical.application.port.out.RecordHandoverRepositoryPort;
import com.mediflow.clinical.application.port.out.StaffLookupPort;
import com.mediflow.clinical.domain.model.MedicalRecord;
import com.mediflow.clinical.domain.model.RecordClosureReason;
import com.mediflow.clinical.domain.model.RecordHandover;
import com.mediflow.clinical.domain.model.RecordHandoverStatus;
import com.mediflow.common.exception.ForbiddenOperationException;

class RecordHandoverApplicationServiceTest {
    static final Instant NOW = Instant.parse("2026-11-10T03:00:00Z");
    final UUID department = UUID.randomUUID();
    final UUID doctor = UUID.randomUUID();
    final UUID receiver = UUID.randomUUID();

    final RecordHandoverRepositoryPort handovers = mock(RecordHandoverRepositoryPort.class);
    final MedicalRecordRepositoryPort records = mock(MedicalRecordRepositoryPort.class);
    final CurrentClinicalActorPort actors = mock(CurrentClinicalActorPort.class);
    final StaffLookupPort staff = mock(StaffLookupPort.class);
    final RecordReadGuard guard = mock(RecordReadGuard.class);
    final IdempotentCommandPort idempotency = mock(IdempotentCommandPort.class);
    final RecordHandoverApplicationService service = new RecordHandoverApplicationService(handovers, records, actors,
            staff, guard, true, idempotency, Clock.fixed(NOW, ZoneId.of("Asia/Ho_Chi_Minh")));
    final MedicalRecord record = MedicalRecord.openForAppointment(UUID.randomUUID(), doctor, department,
            LocalDate.parse("2026-11-10"), "Sốt", UUID.randomUUID(), NOW);

    @BeforeEach void setUp() {
        as(doctor);
        lenient().when(staff.departmentOf(doctor)).thenReturn(Optional.of(department));
        lenient().when(staff.departmentOf(receiver)).thenReturn(Optional.of(department));
        lenient().when(records.findByIdForUpdate(record.getRecordId())).thenReturn(Optional.of(record));
        lenient().when(records.findById(record.getRecordId())).thenReturn(Optional.of(record));
        lenient().when(records.save(any())).thenAnswer(call -> call.getArgument(0));
        lenient().when(idempotency.execute(anyString(), anyString(), anyString(), anyString(), anyString(), any(), anyInt(),
                any(), any())).thenAnswer(call -> new IdempotentCommandPort.Result<>(
                        ((Supplier<?>) call.getArgument(8)).get(), call.getArgument(6), null, false));
    }

    private void as(UUID staffId) {
        lenient().when(actors.current()).thenReturn(new ActorIdentity(UUID.randomUUID(), staffId, null, "DOCTOR"));
    }

    private RequestHandoverRequest request(UUID to) {
        return new RequestHandoverRequest(to, "Hết ca", "Chờ kết quả công thức máu");
    }

    @Test void responsibleDoctorRequestsHandover() {
        var result = service.request(record.getRecordId(), request(receiver), "k");
        assertThat(result.status()).isEqualTo(201);
        assertThat(result.data().toDoctorId()).isEqualTo(receiver);
        assertThat(result.data().status()).isEqualTo("PENDING");
        verify(handovers).insert(any());
    }

    @Test void receiverMustBeSameDepartment() {
        UUID elsewhere = UUID.randomUUID();
        when(staff.departmentOf(elsewhere)).thenReturn(Optional.of(UUID.randomUUID()));
        assertThatThrownBy(() -> service.request(record.getRecordId(), request(elsewhere), "k"))
                .extracting("code").isEqualTo("HANDOVER_INVALID");
        UUID unknown = UUID.randomUUID();
        when(staff.departmentOf(unknown)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.request(record.getRecordId(), request(unknown), "k"))
                .extracting("code").isEqualTo("HANDOVER_INVALID");
        verify(handovers, never()).insert(any());
    }

    @Test void onlyResponsibleDoctorRequests() {
        UUID colleague = UUID.randomUUID();
        when(staff.departmentOf(colleague)).thenReturn(Optional.of(department));
        as(colleague);
        assertThatThrownBy(() -> service.request(record.getRecordId(), request(receiver), "k"))
                .isInstanceOf(ForbiddenOperationException.class);
    }

    @Test void newRequestSupersedesThePendingOne() {
        RecordHandover old = RecordHandover.request(record.getRecordId(), doctor, UUID.randomUUID(), "Hết ca", "Chờ", NOW);
        when(handovers.findPending(record.getRecordId())).thenReturn(Optional.of(old));
        service.request(record.getRecordId(), request(receiver), "k");
        org.mockito.Mockito.verify(handovers).supersede(old);
        assertThat(old.getStatus()).isEqualTo(com.mediflow.clinical.domain.model.RecordHandoverStatus.SUPERSEDED);
        org.mockito.Mockito.verify(handovers).insert(org.mockito.ArgumentMatchers.argThat(h -> h.getToDoctorId().equals(receiver)));
    }

    @Test void closedRecordCannotBeHandedOver() {
        record.closeWithoutConclusion(RecordClosureReason.PATIENT_LEFT, "Tự về", doctor, NOW);
        assertThatThrownBy(() -> service.request(record.getRecordId(), request(receiver), "k"))
                .extracting("code").isEqualTo("CLINICAL_INVALID_STATUS_TRANSITION");
    }

    @Test void receiverAcceptsAndBecomesResponsible() {
        RecordHandover pending = RecordHandover.request(record.getRecordId(), doctor, receiver, "Hết ca", "Chờ", NOW);
        when(handovers.findByIdForUpdate(pending.getHandoverId())).thenReturn(Optional.of(pending));
        as(receiver);
        var result = service.accept(record.getRecordId(), pending.getHandoverId(), "k");
        assertThat(result.data().status()).isEqualTo("ACCEPTED");
        assertThat(record.responsibleDoctorId()).isEqualTo(receiver);
        assertThat(record.getDoctorId()).isEqualTo(doctor);
        verify(records).save(record);
        verify(handovers).accept(pending);
        assertThat(pending.getStatus()).isEqualTo(RecordHandoverStatus.ACCEPTED);
    }

    @Test void previousDoctorCannotAcceptForReceiver() {
        RecordHandover pending = RecordHandover.request(record.getRecordId(), doctor, receiver, "Hết ca", "Chờ", NOW);
        when(handovers.findByIdForUpdate(pending.getHandoverId())).thenReturn(Optional.of(pending));
        assertThatThrownBy(() -> service.accept(record.getRecordId(), pending.getHandoverId(), "k"))
                .isInstanceOf(ForbiddenOperationException.class);
        assertThat(record.responsibleDoctorId()).isEqualTo(doctor);
    }

    @Test void handoverOfAnotherRecordIsNotFound() {
        RecordHandover other = RecordHandover.request(UUID.randomUUID(), doctor, receiver, "Hết ca", "Chờ", NOW);
        when(handovers.findByIdForUpdate(other.getHandoverId())).thenReturn(Optional.of(other));
        as(receiver);
        assertThatThrownBy(() -> service.accept(record.getRecordId(), other.getHandoverId(), "k"))
                .isInstanceOf(com.mediflow.common.exception.ResourceNotFoundException.class);
    }

    @Test void listNeedsRecordRead() {
        when(guard.requireRecord(record)).thenThrow(new ForbiddenOperationException("RECORD_READ_DENIED", "no"));
        assertThatThrownBy(() -> service.list(record.getRecordId())).isInstanceOf(ForbiddenOperationException.class);
        when(handovers.findByRecord(record.getRecordId())).thenReturn(List.of());
    }
}
