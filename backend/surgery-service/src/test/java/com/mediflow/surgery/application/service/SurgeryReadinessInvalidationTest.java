package com.mediflow.surgery.application.service;

import com.mediflow.surgery.application.exception.SurgeryRevisionConflictException;
import com.mediflow.surgery.application.port.out.SurgeryResourceReservationPort;
import com.mediflow.surgery.application.port.out.SurgeryScheduleRepositoryPort;
import com.mediflow.surgery.domain.model.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class SurgeryReadinessInvalidationTest {
    private static final Instant NOW = Instant.parse("2026-10-05T08:00:00Z");
    private final SurgeryScheduleRepositoryPort schedules = mock(SurgeryScheduleRepositoryPort.class);
    private final SurgeryResourceReservationPort reservations = mock(SurgeryResourceReservationPort.class);
    private final SurgeryAuditActor actor = SurgeryAuditActor.system("surgery-service");

    @ParameterizedTest
    @EnumSource(value = SurgeryStatus.class, names = {"READY", "SCHEDULED"})
    void matchingDependencyInvalidatesAndOnlyScheduledReleases(SurgeryStatus status) {
        var fixture = fixture(status);
        when(schedules.findByCaseId(fixture.surgeryCase().getSurgeryCaseId())).thenReturn(Optional.of(fixture.schedule()));
        invalidate(fixture.surgeryCase());
        assertThat(fixture.surgeryCase().getStatus()).isEqualTo(SurgeryStatus.PREOP_IN_PROGRESS);
        assertThat(fixture.surgeryCase().getReadinessSnapshot()).isNull();
        if (status == SurgeryStatus.SCHEDULED) verify(reservations).release(fixture.surgeryCase().getSurgeryCaseId(),
                fixture.schedule().scheduleId(), fixture.schedule().revision(), NOW);
        else verifyNoInteractions(reservations);
    }

    @Test void newerScheduleRevisionCannotReleaseCurrentBookingUnderOldSnapshot() {
        var fixture = fixture(SurgeryStatus.SCHEDULED);
        assertConflictWithoutEffects(fixture, new SurgerySchedule(fixture.schedule().scheduleId(),
                fixture.schedule().surgeryCaseId(), 2, fixture.schedule().roomId(),
                fixture.schedule().startsAt(), fixture.schedule().endsAt(), fixture.schedule().teamAssignments()));
    }

    @Test void anotherScheduleCannotReleaseCurrentBookingUnderOldSnapshot() {
        var fixture = fixture(SurgeryStatus.SCHEDULED);
        assertConflictWithoutEffects(fixture, new SurgerySchedule(UUID.randomUUID(),
                fixture.schedule().surgeryCaseId(), 1, fixture.schedule().roomId(),
                fixture.schedule().startsAt(), fixture.schedule().endsAt(), fixture.schedule().teamAssignments()));
    }

    @Test void foreignCaseScheduleCannotBeInvalidated() {
        var fixture = fixture(SurgeryStatus.SCHEDULED);
        assertConflictWithoutEffects(fixture, new SurgerySchedule(fixture.schedule().scheduleId(),
                UUID.randomUUID(), 1, fixture.schedule().roomId(),
                fixture.schedule().startsAt(), fixture.schedule().endsAt(), fixture.schedule().teamAssignments()));
    }

    @Test void missingScheduleCannotClearReadinessOrReleaseAnything() {
        var fixture = fixture(SurgeryStatus.SCHEDULED);
        when(schedules.findByCaseId(fixture.surgeryCase().getSurgeryCaseId())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> invalidate(fixture.surgeryCase())).isInstanceOf(SurgeryRevisionConflictException.class);
        assertThat(fixture.surgeryCase().getReadinessSnapshot()).isNotNull();
        verifyNoInteractions(reservations);
    }

    @Test void startedCaseIsNeverRegressedOrReleasedByInvalidation() {
        var fixture = fixture(SurgeryStatus.SCHEDULED);
        fixture.surgeryCase().start(fixture.surgeryCase().getReadinessSnapshot(), actor, "invalidation-test", NOW.minusSeconds(1));
        invalidate(fixture.surgeryCase());
        assertThat(fixture.surgeryCase().getStatus()).isEqualTo(SurgeryStatus.IN_PROGRESS);
        verifyNoInteractions(schedules, reservations);
    }

    private void assertConflictWithoutEffects(Fixture fixture, SurgerySchedule returned) {
        when(schedules.findByCaseId(fixture.surgeryCase().getSurgeryCaseId())).thenReturn(Optional.of(returned));
        long revision = fixture.surgeryCase().getRevision();
        var snapshot = fixture.surgeryCase().getReadinessSnapshot();
        assertThatThrownBy(() -> invalidate(fixture.surgeryCase())).isInstanceOf(SurgeryRevisionConflictException.class);
        assertThat(fixture.surgeryCase().getStatus()).isEqualTo(SurgeryStatus.SCHEDULED);
        assertThat(fixture.surgeryCase().getRevision()).isEqualTo(revision);
        assertThat(fixture.surgeryCase().getReadinessSnapshot()).isEqualTo(snapshot);
        verifyNoInteractions(reservations);
    }
    private void invalidate(SurgeryCase surgeryCase) {
        SurgeryReadinessInvalidation.invalidateIfRequired(surgeryCase, actor, "invalidation-test", NOW,
                "READINESS_EXPIRED", schedules, reservations);
    }
    private Fixture fixture(SurgeryStatus status) {
        var surgeryCase = SurgeryCase.create(UUID.randomUUID(), UUID.randomUUID(),
                new CareEpisode(CareEpisodeType.OUTPATIENT_VISIT, UUID.randomUUID(), null, UUID.randomUUID()),
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "TEST-PROC", "Test-only indication",
                SurgeryPriority.ROUTINE, NOW.minusSeconds(30), actor, "invalidation-test");
        surgeryCase.beginPreop(actor, "invalidation-test", NOW.minusSeconds(20));
        var schedule = new SurgerySchedule(UUID.randomUUID(), surgeryCase.getSurgeryCaseId(), 1, UUID.randomUUID(),
                NOW.plusSeconds(3600), NOW.plusSeconds(5400), List.of(
                new SurgeryTeamAssignment(UUID.randomUUID(), SurgeryTeamRole.PRIMARY_SURGEON)));
        var snapshot = ReadinessSnapshot.evaluate(UUID.randomUUID(), surgeryCase.getSurgeryCaseId(),
                true, true, true, true, true, true, true, NOW.minusSeconds(10),
                Arrays.stream(SurgeryDependencyType.values()).map(type -> new SurgeryDependencyRevision(type,
                        type == SurgeryDependencyType.SCHEDULE ? schedule.scheduleId() : UUID.randomUUID(), 1)).toList(),
                NOW.plusSeconds(600));
        surgeryCase.markReady(snapshot, actor, "invalidation-test");
        if (status == SurgeryStatus.SCHEDULED) surgeryCase.finalizeSchedule(actor, "invalidation-test", NOW.minusSeconds(5));
        return new Fixture(surgeryCase, schedule);
    }
    private record Fixture(SurgeryCase surgeryCase, SurgerySchedule schedule) { }
}
