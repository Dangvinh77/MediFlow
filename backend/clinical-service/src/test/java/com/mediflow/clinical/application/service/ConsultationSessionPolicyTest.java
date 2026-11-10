package com.mediflow.clinical.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.mediflow.clinical.application.dto.request.ConsultationSessionCreateRequest;
import com.mediflow.clinical.application.dto.request.ConsultationSessionStatusRequest;
import com.mediflow.clinical.application.port.out.DoctorWorkShiftRepositoryPort;
import com.mediflow.clinical.domain.model.ConsultationSession;
import com.mediflow.clinical.domain.model.ConsultationSessionStatus;
import com.mediflow.clinical.domain.model.DoctorWorkShift;
import com.mediflow.common.exception.BusinessRuleException;
import com.mediflow.common.exception.DuplicateResourceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ConsultationSessionPolicyTest {
    private final IntakeHarness h = new IntakeHarness();
    private final DoctorWorkShiftRepositoryPort shifts = mock(DoctorWorkShiftRepositoryPort.class);
    private final LocalDate yesterday = IntakeHarness.TODAY.minusDays(1);

    private ConsultationSessionApplicationService schedules(boolean flag) {
        return new ConsultationSessionApplicationService(h.sessions, h.authority, h.actors, h.correlationIds, h.idempotency,
                h.clock, new ConsultationSessionPolicy(shifts, new ClinicIntakeSettings(flag), h.clock));
    }

    private DoctorWorkShiftApplicationService shiftService(boolean flag) {
        return new DoctorWorkShiftApplicationService(shifts, h.sessions, schedules(flag), h.idempotency, h.clock,
                new ConsultationSessionPolicy(shifts, new ClinicIntakeSettings(flag), h.clock), new ClinicIntakeSettings(flag));
    }

    private ConsultationSessionCreateRequest request(LocalDate date, UUID room, int startHour, int endHour) {
        return new ConsultationSessionCreateRequest(h.doctorId, h.departmentId, room, date, LocalTime.of(startHour, 0),
                LocalTime.of(endHour, 0), List.of(), 20);
    }

    private DoctorWorkShift shift(LocalDate date, UUID room) {
        var shift = DoctorWorkShift.active(UUID.randomUUID(), h.doctorId, h.departmentId, room, date,
                LocalTime.of(8, 0), LocalTime.of(12, 0));
        lenient().when(shifts.findById(shift.shiftId())).thenReturn(Optional.of(shift));
        lenient().when(shifts.findByIdForUpdate(shift.shiftId())).thenReturn(Optional.of(shift));
        return shift;
    }

    private void create(boolean fromShift, LocalDate date, boolean flag) {
        if (fromShift) shiftService(flag).createSession(shift(date, h.roomId).shiftId(), request(date, h.roomId, 8, 11), "key");
        else schedules(flag).create(request(date, h.roomId, 8, 11), "key");
    }

    @BeforeEach void signIn() {
        h.actAsManager();
        lenient().when(h.sessions.insert(any(), any(), any(), any())).thenAnswer(call -> call.getArgument(0));
    }

    @ParameterizedTest @ValueSource(booleans = {true, false})
    void bothCreationPathsApplyTheSamePolicy(boolean fromShift) {
        assertThatThrownBy(() -> create(fromShift, yesterday, true))
                .isInstanceOfSatisfying(BusinessRuleException.class, e -> assertThat(e.getCode()).isEqualTo("SESSION_DATE_PAST"));
        when(h.authority.consultationEligibility(any(), any(), any(), any()))
                .thenReturn(h.eligibility(h.doctorId, h.departmentId, h.roomId, false));
        assertThatThrownBy(() -> create(fromShift, IntakeHarness.TODAY, true))
                .isInstanceOfSatisfying(BusinessRuleException.class, e -> assertThat(e.getCode()).isEqualTo("SCHEDULE_INELIGIBLE"));
    }

    @Test void standaloneSessionConflictingWithActiveShiftIsRejected() {
        var otherRoomShift = shift(IntakeHarness.TODAY, UUID.randomUUID());
        when(shifts.findByDoctor(h.doctorId, IntakeHarness.TODAY, IntakeHarness.TODAY)).thenReturn(List.of(otherRoomShift));
        assertThatThrownBy(() -> schedules(true).create(request(IntakeHarness.TODAY, h.roomId, 9, 11), "key"))
                .isInstanceOfSatisfying(BusinessRuleException.class, e -> assertThat(e.getCode()).isEqualTo("SESSION_SHIFT_CONFLICT"));
    }

    @Test void standaloneSessionWithoutShiftIsAllowed() {
        when(shifts.findByDoctor(h.doctorId, IntakeHarness.TODAY, IntakeHarness.TODAY)).thenReturn(List.of());
        assertThat(schedules(true).create(request(IntakeHarness.TODAY, h.roomId, 8, 11), "key").status()).isEqualTo(201);
    }

    @Test void adjacentSessionsAreAllowed() {
        var otherRoomShift = shift(IntakeHarness.TODAY, UUID.randomUUID());
        when(shifts.findByDoctor(h.doctorId, IntakeHarness.TODAY, IntakeHarness.TODAY)).thenReturn(List.of(otherRoomShift));
        assertThat(schedules(true).create(request(IntakeHarness.TODAY, h.roomId, 12, 14), "key").status()).isEqualTo(201);
    }

    @Test void linkedSessionCannotLeaveItsShift() {
        var shift = shift(IntakeHarness.TODAY, h.roomId);
        UUID session = UUID.randomUUID();
        when(shifts.findBySession(session)).thenReturn(Optional.of(shift));
        var outside = new ConsultationSession.Configuration(h.doctorId, h.departmentId, UUID.randomUUID(), IntakeHarness.TODAY,
                LocalTime.of(8, 0), LocalTime.of(11, 0), List.of(), 20);
        var policy = new ConsultationSessionPolicy(shifts, new ClinicIntakeSettings(true), h.clock);
        assertThatThrownBy(() -> policy.requireUpdateAllowed(session, outside))
                .isInstanceOfSatisfying(DuplicateResourceException.class, e -> assertThat(e.getCode()).isEqualTo("SHIFT_SESSION_MISMATCH"));
    }

    @Test void stoppedSessionOfCancelledShiftCannotReopen() {
        var stopped = h.session().changeStatus(1, ConsultationSessionStatus.STOPPED, "Bác sĩ nghỉ");
        h.stub(stopped);
        var cancelled = shift(IntakeHarness.TODAY, h.roomId).cancel(1, "Nghỉ");
        when(shifts.findBySession(stopped.sessionId())).thenReturn(Optional.of(cancelled));
        assertThatThrownBy(() -> schedules(true).changeStatus(stopped.sessionId(),
                new ConsultationSessionStatusRequest(stopped.revision(), ConsultationSessionStatus.OPEN, "Mở lại"), "key"))
                .isInstanceOfSatisfying(DuplicateResourceException.class, e -> assertThat(e.getCode()).isEqualTo("SHIFT_CANCELLED"));
    }

    @Test void flagOffKeepsExistingCreation() {
        assertThat(schedules(false).create(request(yesterday, h.roomId, 8, 11), "key").status()).isEqualTo(201);
    }
}
