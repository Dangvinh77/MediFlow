package com.mediflow.surgery.application.service;

import com.mediflow.surgery.application.port.in.QueryExpiredSurgeryReadinessUseCase.Candidate;
import com.mediflow.surgery.application.port.out.SurgeryCaseRepositoryPort;
import com.mediflow.surgery.application.port.out.SurgeryClockPort;
import com.mediflow.surgery.application.port.out.SurgeryResourceReservationPort;
import com.mediflow.surgery.application.port.out.SurgeryScheduleRepositoryPort;
import com.mediflow.surgery.domain.model.CareEpisode;
import com.mediflow.surgery.domain.model.CareEpisodeType;
import com.mediflow.surgery.domain.model.ReadinessSnapshot;
import com.mediflow.surgery.domain.model.SurgeryAuditActor;
import com.mediflow.surgery.domain.model.SurgeryCase;
import com.mediflow.surgery.domain.model.SurgeryDependencyRevision;
import com.mediflow.surgery.domain.model.SurgeryDependencyType;
import com.mediflow.surgery.domain.model.SurgeryPriority;
import com.mediflow.surgery.domain.model.SurgerySchedule;
import com.mediflow.surgery.domain.model.SurgeryStatus;
import com.mediflow.surgery.domain.model.SurgeryTeamAssignment;
import com.mediflow.surgery.domain.model.SurgeryTeamRole;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SurgeryReadinessExpiryServiceTest {
    private static final Instant NOW = Instant.parse("2026-10-05T08:00:00Z");
    @Mock private SurgeryCaseRepositoryPort cases;
    @Mock private SurgeryScheduleRepositoryPort schedules;
    @Mock private SurgeryResourceReservationPort resources;
    @Mock private SurgeryClockPort clock;
    private SurgeryReadinessExpiryService service;
    private SurgeryCase value;
    private SurgerySchedule schedule;
    private ReadinessSnapshot snapshot;
    private SurgeryAuditActor actor;

    @BeforeEach void setup() {
        service = new SurgeryReadinessExpiryService(cases, schedules, resources, clock);
        actor = SurgeryAuditActor.human(UUID.randomUUID(), UUID.randomUUID());
        value = SurgeryCase.create(UUID.randomUUID(), UUID.randomUUID(),
                new CareEpisode(CareEpisodeType.OUTPATIENT_VISIT, UUID.randomUUID(), null, UUID.randomUUID()),
                UUID.randomUUID(), UUID.randomUUID(), actor.verifiedStaffId(), "EXPIRY-TEST", "Test indication",
                SurgeryPriority.ROUTINE, NOW.minusSeconds(60), actor, "expiry-test");
        value.beginPreop(actor, "expiry-test", NOW.minusSeconds(50));
        schedule = new SurgerySchedule(UUID.randomUUID(), value.getSurgeryCaseId(), 1, UUID.randomUUID(),
                NOW.plusSeconds(60), NOW.plusSeconds(120),
                List.of(new SurgeryTeamAssignment(actor.verifiedStaffId(), SurgeryTeamRole.PRIMARY_SURGEON)));
        snapshot = snapshot(NOW);
        value.markReady(snapshot, actor, "expiry-test");
    }

    @Test void expire_atDeadlineInvalidatesReadyOnceWithoutResourceRelease() {
        due();
        assertThat(service.expire(candidate(), "expiry-test")).isTrue();
        assertThat(value.getStatus()).isEqualTo(SurgeryStatus.PREOP_IN_PROGRESS);
        assertThat(value.getReadinessSnapshot()).isNull();
        verify(cases).save(value, 2);
        verifyNoInteractions(resources);
        assertThat(service.expire(candidate(), "expiry-repeat")).isFalse();
        verify(cases, times(1)).save(value, 2);
    }

    @Test void expire_scheduledCaseReleasesOnlyExactBooking() {
        value.finalizeSchedule(actor, "expiry-test", NOW.minusSeconds(20));
        due();
        assertThat(service.expire(candidate(), "expiry-test")).isTrue();
        verify(resources).release(value.getSurgeryCaseId(), schedule.scheduleId(), 1, NOW);
        verify(cases).save(value, 3);
    }

    @Test void expire_beforeDeadlineDoesNotMutate() {
        when(cases.lockById(value.getSurgeryCaseId())).thenReturn(Optional.of(value));
        when(clock.now()).thenReturn(NOW.minusNanos(1));
        assertThat(service.expire(candidate(), "expiry-test")).isFalse();
        verifyNoInteractions(schedules, resources);
        verify(cases, never()).save(any(), anyLong());
    }

    @Test void expire_snapshotWithoutValidityDoesNotInventTtl() {
        value.invalidateReadiness(actor, "expiry-test", NOW.minusSeconds(30), "TEST_REPLACEMENT");
        value.markReady(snapshot(null), actor, "expiry-test");
        when(cases.lockById(value.getSurgeryCaseId())).thenReturn(Optional.of(value));
        when(clock.now()).thenReturn(NOW.plusSeconds(999));
        assertThat(service.expire(new Candidate(value.getSurgeryCaseId(), value.getReadinessSnapshot().snapshotId()), "expiry-test")).isFalse();
        verifyNoInteractions(schedules, resources);
    }

    @Test void expire_replacedSnapshotDoesNotInvalidateNewWinner() {
        when(cases.lockById(value.getSurgeryCaseId())).thenReturn(Optional.of(value));
        assertThat(service.expire(new Candidate(value.getSurgeryCaseId(), UUID.randomUUID()), "expiry-test")).isFalse();
        verifyNoInteractions(clock, schedules, resources);
    }

    @Test void expire_startedCaseRetainsReadinessAndDoesNotReleaseInUse() {
        value.finalizeSchedule(actor, "expiry-test", NOW.minusSeconds(20));
        value.start(snapshot, actor, "expiry-test", NOW.minusSeconds(10));
        when(cases.lockById(value.getSurgeryCaseId())).thenReturn(Optional.of(value));
        assertThat(service.expire(candidate(), "expiry-test")).isFalse();
        assertThat(value.getReadinessSnapshot()).isEqualTo(snapshot);
        verifyNoInteractions(clock, schedules, resources);
    }

    @Test void expire_clockIsReadAfterCaseLock() {
        when(cases.lockById(value.getSurgeryCaseId())).thenAnswer(call -> {
            when(clock.now()).thenReturn(NOW);
            return Optional.of(value);
        });
        when(schedules.findByCaseId(value.getSurgeryCaseId())).thenReturn(Optional.of(schedule));
        assertThat(service.expire(candidate(), "expiry-test")).isTrue();
        var ordered = inOrder(cases, clock);
        ordered.verify(cases).lockById(value.getSurgeryCaseId());
        ordered.verify(clock).now();
    }

    @Test void expire_unknownCaseIsNoOp() {
        when(cases.lockById(value.getSurgeryCaseId())).thenReturn(Optional.empty());
        assertThat(service.expire(candidate(), "expiry-test")).isFalse();
        verifyNoInteractions(clock, schedules, resources);
    }

    @Test void expire_staleScheduleFailsWithoutReleasingResource() {
        when(cases.lockById(value.getSurgeryCaseId())).thenReturn(Optional.of(value));
        when(clock.now()).thenReturn(NOW);
        when(schedules.findByCaseId(value.getSurgeryCaseId())).thenReturn(Optional.of(new SurgerySchedule(
                schedule.scheduleId(), value.getSurgeryCaseId(), 2, schedule.roomId(),
                schedule.startsAt(), schedule.endsAt(), schedule.teamAssignments())));
        assertThatThrownBy(() -> service.expire(candidate(), "expiry-test"))
                .isInstanceOf(com.mediflow.surgery.application.exception.SurgeryRevisionConflictException.class);
        assertThat(value.getStatus()).isEqualTo(SurgeryStatus.READY);
        verifyNoInteractions(resources);
        verify(cases, never()).save(any(), anyLong());
    }

    @Test void expire_invalidInputFailsBeforePorts() {
        assertThatThrownBy(() -> service.expire(null, "expiry-test")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.expire(candidate(), " ")).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(cases, clock, schedules, resources);
    }

    private Candidate candidate() { return new Candidate(value.getSurgeryCaseId(), snapshot.snapshotId()); }
    private void due() {
        when(cases.lockById(value.getSurgeryCaseId())).thenReturn(Optional.of(value));
        when(clock.now()).thenReturn(NOW);
        when(schedules.findByCaseId(value.getSurgeryCaseId())).thenReturn(Optional.of(schedule));
    }
    private ReadinessSnapshot snapshot(Instant validity) {
        return ReadinessSnapshot.evaluate(UUID.randomUUID(), value.getSurgeryCaseId(),
                true, true, true, true, true, true, true, NOW.minusSeconds(30),
                Arrays.stream(SurgeryDependencyType.values()).map(type -> type == SurgeryDependencyType.SCHEDULE
                        ? new SurgeryDependencyRevision(type, schedule.scheduleId(), schedule.revision())
                        : new SurgeryDependencyRevision(type, UUID.randomUUID(), 1)).toList(), validity);
    }
}
