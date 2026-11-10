package com.mediflow.clinical.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.mediflow.clinical.application.dto.request.DoctorWorkShiftCancelRequest;
import com.mediflow.clinical.application.dto.request.DoctorWorkShiftUpdateRequest;
import com.mediflow.clinical.application.port.out.DoctorWorkShiftRepositoryPort;
import com.mediflow.clinical.domain.model.ConsultationSession;
import com.mediflow.clinical.domain.model.ConsultationSessionStatus;
import com.mediflow.clinical.domain.model.DoctorWorkShift;
import com.mediflow.clinical.domain.model.OutpatientReceptionMode;
import com.mediflow.common.api.PageResult;
import com.mediflow.common.exception.DuplicateResourceException;
import com.mediflow.common.exception.ForbiddenOperationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ShiftImpactCascadeTest {
    private final IntakeHarness h = new IntakeHarness();
    private final DoctorWorkShiftRepositoryPort shifts = mock(DoctorWorkShiftRepositoryPort.class);
    private DoctorWorkShift shift;
    private ConsultationSession first;
    private ConsultationSession second;

    private ConsultationSessionApplicationService schedules(boolean flag) {
        return new ConsultationSessionApplicationService(h.sessions, h.authority, h.actors, h.correlationIds, h.idempotency,
                h.clock, new ConsultationSessionPolicy(shifts, new ClinicIntakeSettings(flag), h.clock));
    }

    private DoctorWorkShiftApplicationService service(boolean flag) {
        return new DoctorWorkShiftApplicationService(shifts, h.sessions, schedules(flag), h.idempotency, h.clock,
                new ConsultationSessionPolicy(shifts, new ClinicIntakeSettings(flag), h.clock), new ClinicIntakeSettings(flag));
    }

    private ScheduleImpactApplicationService impact() {
        return new ScheduleImpactApplicationService(h.sessions, shifts, h.registrations, schedules(true));
    }

    @BeforeEach void setUp() {
        h.actAsManager();
        shift = DoctorWorkShift.active(UUID.randomUUID(), h.doctorId, h.departmentId, h.roomId, IntakeHarness.TODAY,
                LocalTime.of(8, 0), LocalTime.of(12, 0));
        lenient().when(shifts.findById(shift.shiftId())).thenReturn(Optional.of(shift));
        lenient().when(shifts.findByIdForUpdate(shift.shiftId())).thenReturn(Optional.of(shift));
        first = h.withCounts(h.session(), 0, 0, 2, 0);
        second = h.session();
        lenient().when(shifts.linkedSessionIds(shift.shiftId())).thenReturn(List.of(first.sessionId(), second.sessionId()));
    }

    @Test void cancellingShiftStopsItsOpenSessions() {
        service(true).cancel(shift.shiftId(), new DoctorWorkShiftCancelRequest(1, "Bác sĩ nghỉ đột xuất"), "key");
        var captor = ArgumentCaptor.forClass(ConsultationSession.class);
        verify(h.sessions, org.mockito.Mockito.times(2)).update(any(), captor.capture(), any(), eq("STATUS_CHANGED"),
                eq("Bác sĩ nghỉ đột xuất"), any());
        assertThat(captor.getAllValues()).allMatch(s -> s.status() == ConsultationSessionStatus.STOPPED);
        verify(shifts).cancelKeepingStoppedSessions(eq(shift), any(), any(), eq("Bác sĩ nghỉ đột xuất"), any());
    }

    @Test void cancellingShiftKeepsExistingRegistrations() {
        service(true).cancel(shift.shiftId(), new DoctorWorkShiftCancelRequest(1, "Bác sĩ nghỉ"), "key");
        var captor = ArgumentCaptor.forClass(ConsultationSession.class);
        verify(h.sessions, org.mockito.Mockito.atLeastOnce()).update(any(), captor.capture(), any(), any(), any(), any());
        assertThat(captor.getAllValues()).filteredOn(s -> s.sessionId().equals(first.sessionId()))
                .singleElement().satisfies(s -> assertThat(s.counts().confirmedCount()).isEqualTo(2));
        verify(h.registrations, never()).update(any(), any(), any(), any(), any(), any());
    }

    @Test void updatingShiftAwayFromLinkedSessionIsRefused() {
        doThrow(new DuplicateResourceException("SHIFT_HAS_SESSIONS", "Cancel linked consultation sessions before changing the shift"))
                .when(shifts).update(any(), any(), any(), any(), any());
        assertThatThrownBy(() -> service(true).update(shift.shiftId(), new DoctorWorkShiftUpdateRequest(h.doctorId,
                h.departmentId, UUID.randomUUID(), IntakeHarness.TODAY, LocalTime.of(8, 0), LocalTime.of(12, 0), 1, "Đổi phòng"), "key"))
                .isInstanceOfSatisfying(DuplicateResourceException.class, e -> assertThat(e.getCode()).isEqualTo("SHIFT_HAS_SESSIONS"));
    }

    @Test void impactListsAffectedRegistrations() {
        var confirmed = h.stub(h.confirmed(first, OutpatientReceptionMode.PREBOOKED));
        var active = h.stub(h.held(first, OutpatientReceptionMode.AT_COUNTER));
        var cancelled = active.cancel(1, IntakeHarness.ACTOR, null, "Bệnh nhân đổi ý", IntakeHarness.NOW);
        when(h.registrations.findBySession(eq(first.sessionId()), any()))
                .thenReturn(PageResult.of(List.of(confirmed, active, cancelled), 3, 0, 100));
        when(h.registrations.findBySession(eq(second.sessionId()), any())).thenReturn(PageResult.of(List.of(), 0, 0, 100));

        var bySession = impact().forSession(first.sessionId());
        assertThat(bySession.sessionIds()).containsExactly(first.sessionId());
        assertThat(bySession.confirmedCount()).isEqualTo(2);
        assertThat(bySession.registrations()).extracting("registrationId")
                .containsExactly(confirmed.registrationId(), active.registrationId());

        var byShift = impact().forShift(shift.shiftId());
        assertThat(byShift.sessionIds()).containsExactly(first.sessionId(), second.sessionId());
        assertThat(byShift.registrations()).hasSize(2);
    }

    @Test void impactRequiresScheduleScope() {
        h.actAs("DOCTOR", UUID.randomUUID(), h.departmentId);
        assertThatThrownBy(() -> impact().forSession(first.sessionId())).isInstanceOf(ForbiddenOperationException.class);
    }

    @Test void flagOffCancelLeavesSessionsUntouched() {
        service(false).cancel(shift.shiftId(), new DoctorWorkShiftCancelRequest(1, "Bác sĩ nghỉ"), "key");
        verify(h.sessions, never()).update(any(), any(), any(), any(), any(), any());
        verify(shifts).update(eq(shift), any(), any(), any(), any());
    }
}
