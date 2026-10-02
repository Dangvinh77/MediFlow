package com.mediflow.surgery.domain.model;

import com.mediflow.surgery.domain.exception.SurgeryRuleException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SurgeryCaseTest {

    private static final Instant REQUESTED_AT = Instant.parse("2026-09-28T01:00:00Z");
    private static final UUID ACTOR_ID = UUID.randomUUID();
    private static final SurgeryAuditActor ACTOR = SurgeryAuditActor.human(ACTOR_ID, UUID.randomUUID());
    private static final String CORRELATION_ID = "surgery-case-test";

    @Test
    void create_newCase_startsRequestedAndRecordsInitialHistory() {
        SurgeryCase surgeryCase = newCase();

        assertThat(surgeryCase.getStatus()).isEqualTo(SurgeryStatus.REQUESTED);
        assertThat(surgeryCase.getRevision()).isZero();
        assertThat(surgeryCase.getStatusHistory())
                .singleElement()
                .satisfies(change -> {
                    assertThat(change.previousStatus()).isNull();
                    assertThat(change.newStatus()).isEqualTo(SurgeryStatus.REQUESTED);
                    assertThat(change.performedBy()).isEqualTo(ACTOR_ID);
                });
    }

    @Test
    void markReady_withoutBothTypedConsents_rejectsWithoutChangingState() {
        SurgeryCase surgeryCase = caseInPreop();

        assertThatThrownBy(() -> surgeryCase.markReady(
                readiness(surgeryCase, false, true), ACTOR, CORRELATION_ID))
                .isInstanceOf(SurgeryRuleException.class)
                .hasFieldOrPropertyWithValue("code", "SURGERY_NOT_READY");

        assertThat(surgeryCase.getStatus()).isEqualTo(SurgeryStatus.PREOP_IN_PROGRESS);
        assertThat(surgeryCase.getReadinessSnapshot()).isNull();
        assertThat(surgeryCase.getRevision()).isEqualTo(1);
    }

    @Test
    void markReady_snapshotForDifferentCase_isRejected() {
        SurgeryCase surgeryCase = caseInPreop();
        ReadinessSnapshot foreignSnapshot = new ReadinessSnapshot(
                UUID.randomUUID(), UUID.randomUUID(), true, true, true, true, true, true, true,
                REQUESTED_AT.plusSeconds(10), dependencyRevisions(), null, List.of());

        assertThatThrownBy(() -> surgeryCase.markReady(foreignSnapshot, ACTOR, CORRELATION_ID))
                .isInstanceOf(SurgeryRuleException.class)
                .hasFieldOrPropertyWithValue("code", "SURGERY_READINESS_SNAPSHOT_MISMATCH");
        assertThat(surgeryCase.getStatus()).isEqualTo(SurgeryStatus.PREOP_IN_PROGRESS);
    }

    @Test
    void complete_caseLifecycle_requiresFreshReadinessAtStart() {
        SurgeryCase surgeryCase = caseInPreop();
        ReadinessSnapshot readySnapshot = readiness(surgeryCase, true, true);
        surgeryCase.markReady(readySnapshot, ACTOR, CORRELATION_ID);
        surgeryCase.finalizeSchedule(ACTOR, CORRELATION_ID, readySnapshot.evaluatedAt().plusSeconds(10));

        surgeryCase.start(
                readiness(surgeryCase, true, true, UUID.randomUUID(),
                        readySnapshot.evaluatedAt().plusSeconds(20)),
                ACTOR,
                CORRELATION_ID,
                readySnapshot.evaluatedAt().plusSeconds(30));
        surgeryCase.complete(ACTOR, CORRELATION_ID, readySnapshot.evaluatedAt().plusSeconds(60));

        assertThat(surgeryCase.getStatus()).isEqualTo(SurgeryStatus.COMPLETED);
        assertThat(surgeryCase.getStartedAt()).isEqualTo(readySnapshot.evaluatedAt().plusSeconds(30));
        assertThat(surgeryCase.getCompletedAt()).isEqualTo(readySnapshot.evaluatedAt().plusSeconds(60));
        assertThat(surgeryCase.getStatusHistory())
                .extracting(SurgeryStateChange::newStatus)
                .containsExactly(
                        SurgeryStatus.REQUESTED,
                        SurgeryStatus.PREOP_IN_PROGRESS,
                        SurgeryStatus.READY,
                        SurgeryStatus.SCHEDULED,
                        SurgeryStatus.IN_PROGRESS,
                        SurgeryStatus.COMPLETED);
    }

    @Test
    void start_guardChangedAfterReady_rejects() {
        SurgeryCase surgeryCase = caseInPreop();
        ReadinessSnapshot readySnapshot = readiness(surgeryCase, true, true);
        surgeryCase.markReady(readySnapshot, ACTOR, CORRELATION_ID);
        surgeryCase.finalizeSchedule(ACTOR, CORRELATION_ID, readySnapshot.evaluatedAt().plusSeconds(1));

        assertThatThrownBy(() -> surgeryCase.start(
                readiness(surgeryCase, false, true, UUID.randomUUID(),
                        readySnapshot.evaluatedAt().plusSeconds(2)),
                ACTOR,
                CORRELATION_ID,
                readySnapshot.evaluatedAt().plusSeconds(3)))
                .isInstanceOf(SurgeryRuleException.class)
                .hasFieldOrPropertyWithValue("code", "SURGERY_NOT_READY");

        assertThat(surgeryCase.getStatus()).isEqualTo(SurgeryStatus.SCHEDULED);
        assertThat(surgeryCase.getStartedAt()).isNull();
    }

    @Test
    void invalidateReadiness_afterSchedule_returnsToPreopAndClearsSnapshot() {
        SurgeryCase surgeryCase = caseInPreop();
        ReadinessSnapshot snapshot = readiness(surgeryCase, true, true);
        surgeryCase.markReady(snapshot, ACTOR, CORRELATION_ID);
        surgeryCase.finalizeSchedule(ACTOR, CORRELATION_ID, snapshot.evaluatedAt().plusSeconds(1));

        surgeryCase.invalidateReadiness(
                ACTOR, CORRELATION_ID,
                snapshot.evaluatedAt().plusSeconds(2), "TEAM_ASSIGNMENT_CHANGED");

        assertThat(surgeryCase.getStatus()).isEqualTo(SurgeryStatus.PREOP_IN_PROGRESS);
        assertThat(surgeryCase.getReadinessSnapshot()).isNull();
        assertThat(surgeryCase.getReadyAt()).isNull();
        assertThat(surgeryCase.getRevision()).isEqualTo(4);
    }

    @Test
    void cancel_beforeStart_isAuditedAndPreservesPriorHistory() {
        SurgeryCase surgeryCase = caseInPreop();
        Instant cancelledAt = REQUESTED_AT.plusSeconds(20);

        surgeryCase.cancel(ACTOR, CORRELATION_ID, cancelledAt, "PATIENT_REQUEST");

        assertThat(surgeryCase.getStatus()).isEqualTo(SurgeryStatus.CANCELLED);
        assertThat(surgeryCase.getCancelledByAccountId()).isEqualTo(ACTOR_ID);
        assertThat(surgeryCase.getCancellationReason()).isEqualTo("PATIENT_REQUEST");
        assertThat(surgeryCase.getStatusHistory()).hasSize(3);
        assertThat(surgeryCase.getStatusHistory().getLast().previousStatus())
                .isEqualTo(SurgeryStatus.PREOP_IN_PROGRESS);
        assertThat(surgeryCase.getReadinessSnapshot()).isNull();
        assertThat(surgeryCase.getReadyAt()).isNull();
    }

    @Test
    void cancel_scheduledCaseClearsActiveReadinessButPreservesCancellationStageInHistory() {
        SurgeryCase surgeryCase = caseInPreop();
        ReadinessSnapshot snapshot = readiness(surgeryCase, true, true);
        surgeryCase.markReady(snapshot, ACTOR, CORRELATION_ID);
        surgeryCase.finalizeSchedule(ACTOR, CORRELATION_ID, snapshot.evaluatedAt().plusSeconds(1));

        surgeryCase.cancel(ACTOR, CORRELATION_ID, snapshot.evaluatedAt().plusSeconds(2), "PATIENT_REQUEST");

        assertThat(surgeryCase.getStatus()).isEqualTo(SurgeryStatus.CANCELLED);
        assertThat(surgeryCase.getStatusHistory().getLast().previousStatus()).isEqualTo(SurgeryStatus.SCHEDULED);
        assertThat(surgeryCase.getReadinessSnapshot()).isNull();
        assertThat(surgeryCase.getReadyAt()).isNull();
    }

    @Test
    void cancel_inProgress_isRejectedAndLeavesCaseUnchanged() {
        SurgeryCase surgeryCase = caseInPreop();
        ReadinessSnapshot snapshot = readiness(surgeryCase, true, true);
        surgeryCase.markReady(snapshot, ACTOR, CORRELATION_ID);
        surgeryCase.finalizeSchedule(ACTOR, CORRELATION_ID, snapshot.evaluatedAt().plusSeconds(1));
        surgeryCase.start(
                readiness(surgeryCase, true, true, UUID.randomUUID(),
                        snapshot.evaluatedAt().plusSeconds(2)),
                ACTOR,
                CORRELATION_ID,
                snapshot.evaluatedAt().plusSeconds(3));
        long versionBeforeCancel = surgeryCase.getRevision();

        assertThatThrownBy(() -> surgeryCase.cancel(
                ACTOR, CORRELATION_ID,
                snapshot.evaluatedAt().plusSeconds(4), "REQUESTED_ABORT"))
                .isInstanceOf(SurgeryRuleException.class)
                .hasFieldOrPropertyWithValue("code", "SURGERY_INVALID_TRANSITION");

        assertThat(surgeryCase.getStatus()).isEqualTo(SurgeryStatus.IN_PROGRESS);
        assertThat(surgeryCase.getCancelledAt()).isNull();
        assertThat(surgeryCase.getRevision()).isEqualTo(versionBeforeCancel);
    }

    @Test
    void transition_timeBeforePreviousFact_isRejectedWithoutPartialMutation() {
        SurgeryCase surgeryCase = newCase();

        assertThatThrownBy(() -> surgeryCase.beginPreop(
                ACTOR, CORRELATION_ID, REQUESTED_AT.minusSeconds(1)))
                .isInstanceOf(SurgeryRuleException.class)
                .hasFieldOrPropertyWithValue("code", "SURGERY_INVALID_TIME");

        assertThat(surgeryCase.getStatus()).isEqualTo(SurgeryStatus.REQUESTED);
        assertThat(surgeryCase.getRevision()).isZero();
        assertThat(surgeryCase.getStatusHistory()).hasSize(1);
    }

    private SurgeryCase caseInPreop() {
        SurgeryCase surgeryCase = newCase();
        surgeryCase.beginPreop(ACTOR, CORRELATION_ID, REQUESTED_AT.plusSeconds(5));
        return surgeryCase;
    }

    private SurgeryCase newCase() {
        UUID selectedEpisodeId = UUID.randomUUID();
        UUID recordId = UUID.randomUUID();
        return SurgeryCase.create(
                UUID.randomUUID(),
                UUID.randomUUID(),
                new CareEpisode(CareEpisodeType.OUTPATIENT_VISIT,
                        selectedEpisodeId, null, recordId),
                UUID.randomUUID(),
                UUID.randomUUID(),
                ACTOR_ID,
                "PROC-001",
                "Indication for surgery",
                SurgeryPriority.ROUTINE,
                REQUESTED_AT,
                ACTOR,
                CORRELATION_ID);
    }

    private ReadinessSnapshot readiness(
            SurgeryCase surgeryCase,
            boolean anesthesiaConsentActive,
            boolean financialClearanceValid) {
        return readiness(surgeryCase, anesthesiaConsentActive, financialClearanceValid,
                UUID.randomUUID(), REQUESTED_AT.plusSeconds(10));
    }

    private ReadinessSnapshot readiness(
            SurgeryCase surgeryCase,
            boolean anesthesiaConsentActive,
            boolean financialClearanceValid,
            UUID snapshotId) {
        return readiness(surgeryCase, anesthesiaConsentActive, financialClearanceValid,
                snapshotId, REQUESTED_AT.plusSeconds(10));
    }

    private ReadinessSnapshot readiness(
            SurgeryCase surgeryCase,
            boolean anesthesiaConsentActive,
            boolean financialClearanceValid,
            UUID snapshotId,
            Instant evaluatedAt) {
        return ReadinessSnapshot.evaluate(snapshotId, surgeryCase.getSurgeryCaseId(),
                true, true, true, anesthesiaConsentActive, true, true,
                financialClearanceValid, evaluatedAt, dependencyRevisions(), null);
    }

    private static List<SurgeryDependencyRevision> dependencyRevisions() {
        return java.util.Arrays.stream(SurgeryDependencyType.values())
                .map(type -> new SurgeryDependencyRevision(type, UUID.randomUUID(), 0))
                .toList();
    }
}
