package com.mediflow.surgery.application.service;

import com.mediflow.surgery.application.dto.SurgeryCommandOutcome;
import com.mediflow.surgery.application.dto.SurgeryActorIdentity;
import com.mediflow.surgery.application.exception.SurgeryRevisionConflictException;
import com.mediflow.surgery.application.port.in.CancelSurgeryUseCase;
import com.mediflow.surgery.application.port.out.SurgeryCaseRepositoryPort;
import com.mediflow.surgery.application.port.out.SurgeryClockPort;
import com.mediflow.surgery.application.port.out.SurgeryCommandReceiptPort;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SurgeryCancellationApplicationServiceTest {

    private static final Instant REQUESTED_AT = Instant.parse("2026-09-29T01:00:00Z");
    private static final Instant NOW = REQUESTED_AT.plusSeconds(30);
    private static final String CORRELATION_ID = "surgery-cancel-test";

    @Mock private SurgeryCaseRepositoryPort cases;
    @Mock private SurgeryScheduleRepositoryPort schedules;
    @Mock private SurgeryResourceReservationPort reservations;
    @Mock private SurgeryCommandReceiptPort receipts;
    @Mock private SurgeryClockPort clock;
    @Mock private com.mediflow.surgery.application.port.out.SurgeryCareEventCapturePort events;
    @InjectMocks private SurgeryCancellationApplicationService service;

    private SurgeryAuditActor actor;
    private SurgeryCase scheduledCase;
    private SurgerySchedule schedule;
    private UUID receiptId;

    @BeforeEach
    void setUp() {
        actor = SurgeryAuditActor.human(UUID.randomUUID(), UUID.randomUUID());
        scheduledCase = newCase(actor);
        scheduledCase.beginPreop(actor, CORRELATION_ID, REQUESTED_AT.plusSeconds(5));
        schedule = schedule(scheduledCase.getSurgeryCaseId());
        ReadinessSnapshot snapshot = ReadinessSnapshot.evaluate(UUID.randomUUID(),
                scheduledCase.getSurgeryCaseId(), true, true, true, true, true, true, true,
                REQUESTED_AT.plusSeconds(10), dependencies(schedule), REQUESTED_AT.plusSeconds(1_000));
        scheduledCase.markReady(snapshot, actor, CORRELATION_ID);
        scheduledCase.finalizeSchedule(actor, CORRELATION_ID, REQUESTED_AT.plusSeconds(11));
        receiptId = UUID.randomUUID();
    }

    @Test
    void cancel_scheduledCaseReleasesExactRevisionAndPersistsCancellationReceipt() {
        when(receipts.claim(any(), anyString())).thenReturn(new SurgeryCommandReceiptPort.Claim(
                SurgeryCommandReceiptPort.State.NEW, receiptId, null, null));
        when(cases.lockById(scheduledCase.getSurgeryCaseId())).thenReturn(Optional.of(scheduledCase));
        when(schedules.findByCaseId(scheduledCase.getSurgeryCaseId())).thenReturn(Optional.of(schedule));
        when(clock.now()).thenReturn(NOW);

        var outcome = service.cancel(command(actor, scheduledCase.getRevision()));

        assertThat(outcome.commandCode()).isEqualTo("CANCEL_SURGERY");
        assertThat(outcome.state()).isEqualTo(SurgeryStatus.CANCELLED.name());
        assertThat(outcome.subjectId()).isEqualTo(schedule.scheduleId());
        assertThat(outcome.subjectRevision()).isEqualTo(schedule.revision());
        assertThat(scheduledCase.getStatus()).isEqualTo(SurgeryStatus.CANCELLED);
        assertThat(scheduledCase.getReadinessSnapshot()).isNull();
        assertThat(scheduledCase.getStatusHistory().getLast().previousStatus()).isEqualTo(SurgeryStatus.SCHEDULED);
        verify(reservations).release(scheduledCase.getSurgeryCaseId(), schedule.scheduleId(), 1, NOW);
        verify(cases).save(scheduledCase, 3);
        verify(receipts).complete(eq(receiptId), eq(scheduledCase.getSurgeryCaseId()),
                eq("CANCEL_SURGERY"), any(), eq(NOW));
    }

    @Test
    void cancel_scheduledCaseWithMismatchedReadinessSchedule_failsClosedBeforeRelease() {
        SurgerySchedule otherSchedule = new SurgerySchedule(UUID.randomUUID(),
                scheduledCase.getSurgeryCaseId(), 1, UUID.randomUUID(),
                NOW.plusSeconds(3_600), NOW.plusSeconds(5_400), List.of(
                new SurgeryTeamAssignment(actor.verifiedStaffId(), SurgeryTeamRole.PRIMARY_SURGEON)));
        when(receipts.claim(any(), anyString())).thenReturn(new SurgeryCommandReceiptPort.Claim(
                SurgeryCommandReceiptPort.State.NEW, receiptId, null, null));
        when(cases.lockById(scheduledCase.getSurgeryCaseId())).thenReturn(Optional.of(scheduledCase));
        when(schedules.findByCaseId(scheduledCase.getSurgeryCaseId())).thenReturn(Optional.of(otherSchedule));

        assertThatThrownBy(() -> service.cancel(command(actor, scheduledCase.getRevision())))
                .isInstanceOf(SurgeryRevisionConflictException.class);

        verify(reservations, never()).release(any(), any(), anyLong(), any());
        verify(cases, never()).save(any(), anyLong());
        verify(receipts, never()).complete(any(), any(), anyString(), any(), any());
        verifyNoInteractions(clock);
    }

    @Test
    void cancel_appliedReceiptReplayReturnsOriginalWithoutLockingOrReleasing() {
        SurgeryCommandOutcome original = new SurgeryCommandOutcome("CANCEL_SURGERY",
                scheduledCase.getSurgeryCaseId(), 4, schedule.scheduleId(), 1,
                SurgeryStatus.CANCELLED.name(), NOW, false);
        ReceiptResponse response = new ReceiptResponse();
        SurgeryCommandReceipts.complete(response, receiptId, original);
        when(receipts.claim(any(), anyString())).thenReturn(new SurgeryCommandReceiptPort.Claim(
                SurgeryCommandReceiptPort.State.REPLAY, receiptId, "CANCEL_SURGERY", response.response));

        var replay = service.cancel(command(actor, scheduledCase.getRevision()));

        assertThat(replay).isEqualTo(original.asReplay());
        verifyNoInteractions(cases, schedules, reservations, clock);
    }

    @Test
    void cancel_nullCommandIsRejectedBeforeClaimingReceipt() {
        assertThatThrownBy(() -> service.cancel(null))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(cases, schedules, reservations, receipts, clock);
    }

    private CancelSurgeryUseCase.Command command(SurgeryAuditActor commandActor, long expectedRevision) {
        return new CancelSurgeryUseCase.Command(scheduledCase.getSurgeryCaseId(), expectedRevision,
                " Patient request ", "cancel-1",
                new SurgeryActorIdentity(commandActor.accountId(), commandActor.verifiedStaffId()),
                CORRELATION_ID);
    }

    private static SurgeryCase newCase(SurgeryAuditActor actor) {
        return SurgeryCase.create(UUID.randomUUID(), UUID.randomUUID(),
                new CareEpisode(CareEpisodeType.OUTPATIENT_VISIT, UUID.randomUUID(), null, UUID.randomUUID()),
                UUID.randomUUID(), UUID.randomUUID(), actor.verifiedStaffId(), "PROC-001",
                "Clinical indication", SurgeryPriority.ROUTINE, REQUESTED_AT, actor, CORRELATION_ID);
    }

    private static List<SurgeryDependencyRevision> dependencies(SurgerySchedule schedule) {
        return Arrays.stream(SurgeryDependencyType.values()).map(type ->
                type == SurgeryDependencyType.SCHEDULE
                        ? new SurgeryDependencyRevision(type, schedule.scheduleId(), schedule.revision())
                        : new SurgeryDependencyRevision(type, UUID.randomUUID(), 1)).toList();
    }

    private static SurgerySchedule schedule(UUID caseId) {
        return new SurgerySchedule(UUID.randomUUID(), caseId, 1, UUID.randomUUID(),
                REQUESTED_AT.plusSeconds(3_600), REQUESTED_AT.plusSeconds(5_400), List.of(
                new SurgeryTeamAssignment(UUID.randomUUID(), SurgeryTeamRole.PRIMARY_SURGEON)));
    }

    private static final class ReceiptResponse implements SurgeryCommandReceiptPort {
        private byte[] response;

        @Override
        public Claim claim(Key key, String fingerprint) {
            throw new UnsupportedOperationException("Only response encoding is used in this test");
        }

        @Override
        public void complete(UUID id, UUID caseId, String responseCode, byte[] payload, Instant at) {
            response = payload.clone();
        }
    }
}
