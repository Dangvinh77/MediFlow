package com.mediflow.surgery.domain.model;

import com.mediflow.surgery.domain.exception.SurgeryRuleException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SurgeryCaseTest {

    private static final Instant REQUESTED_AT = Instant.parse("2026-09-28T01:00:00Z");
    private static final UUID ACTOR_ID = UUID.randomUUID();

    @Test
    void create_newCase_startsRequestedAndRecordsInitialHistory() {
        SurgeryCase surgeryCase = newCase();

        assertThat(surgeryCase.getTrangThai()).isEqualTo(SurgeryStatus.REQUESTED);
        assertThat(surgeryCase.getPhienBan()).isZero();
        assertThat(surgeryCase.getLichSuTrangThai())
                .singleElement()
                .satisfies(change -> {
                    assertThat(change.trangThaiCu()).isNull();
                    assertThat(change.trangThaiMoi()).isEqualTo(SurgeryStatus.REQUESTED);
                    assertThat(change.nguoiThucHien()).isEqualTo(ACTOR_ID);
                });
    }

    @Test
    void markReady_withoutBothTypedConsents_rejectsWithoutChangingState() {
        SurgeryCase surgeryCase = caseInPreop();

        assertThatThrownBy(() -> surgeryCase.markReady(
                readiness(surgeryCase, false, true), ACTOR_ID))
                .isInstanceOf(SurgeryRuleException.class)
                .hasFieldOrPropertyWithValue("code", "SURGERY_NOT_READY");

        assertThat(surgeryCase.getTrangThai()).isEqualTo(SurgeryStatus.PREOP_IN_PROGRESS);
        assertThat(surgeryCase.getAnhChupSanSang()).isNull();
        assertThat(surgeryCase.getPhienBan()).isEqualTo(1);
    }

    @Test
    void markReady_snapshotForDifferentCase_isRejected() {
        SurgeryCase surgeryCase = caseInPreop();
        ReadinessSnapshot foreignSnapshot = new ReadinessSnapshot(
                UUID.randomUUID(),
                UUID.randomUUID(),
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                REQUESTED_AT.plusSeconds(10));

        assertThatThrownBy(() -> surgeryCase.markReady(foreignSnapshot, ACTOR_ID))
                .isInstanceOf(SurgeryRuleException.class)
                .hasFieldOrPropertyWithValue("code", "SURGERY_READINESS_SNAPSHOT_MISMATCH");
        assertThat(surgeryCase.getTrangThai()).isEqualTo(SurgeryStatus.PREOP_IN_PROGRESS);
    }

    @Test
    void complete_caseLifecycle_requiresFreshReadinessAtStart() {
        SurgeryCase surgeryCase = caseInPreop();
        ReadinessSnapshot readySnapshot = readiness(surgeryCase, true, true);
        surgeryCase.markReady(readySnapshot, ACTOR_ID);
        surgeryCase.finalizeSchedule(ACTOR_ID, readySnapshot.evaluatedAt().plusSeconds(10));

        surgeryCase.start(
                readiness(surgeryCase, true, true, UUID.randomUUID(),
                        readySnapshot.evaluatedAt().plusSeconds(20)),
                ACTOR_ID,
                readySnapshot.evaluatedAt().plusSeconds(30));
        surgeryCase.complete(ACTOR_ID, readySnapshot.evaluatedAt().plusSeconds(60));

        assertThat(surgeryCase.getTrangThai()).isEqualTo(SurgeryStatus.COMPLETED);
        assertThat(surgeryCase.getThoiDiemBatDau()).isEqualTo(readySnapshot.evaluatedAt().plusSeconds(30));
        assertThat(surgeryCase.getThoiDiemHoanTat()).isEqualTo(readySnapshot.evaluatedAt().plusSeconds(60));
        assertThat(surgeryCase.getLichSuTrangThai())
                .extracting(SurgeryStateChange::trangThaiMoi)
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
        surgeryCase.markReady(readySnapshot, ACTOR_ID);
        surgeryCase.finalizeSchedule(ACTOR_ID, readySnapshot.evaluatedAt().plusSeconds(1));

        assertThatThrownBy(() -> surgeryCase.start(
                readiness(surgeryCase, false, true, UUID.randomUUID(),
                        readySnapshot.evaluatedAt().plusSeconds(2)),
                ACTOR_ID,
                readySnapshot.evaluatedAt().plusSeconds(3)))
                .isInstanceOf(SurgeryRuleException.class)
                .hasFieldOrPropertyWithValue("code", "SURGERY_NOT_READY");

        assertThat(surgeryCase.getTrangThai()).isEqualTo(SurgeryStatus.SCHEDULED);
        assertThat(surgeryCase.getThoiDiemBatDau()).isNull();
    }

    @Test
    void invalidateReadiness_afterSchedule_returnsToPreopAndClearsSnapshot() {
        SurgeryCase surgeryCase = caseInPreop();
        ReadinessSnapshot snapshot = readiness(surgeryCase, true, true);
        surgeryCase.markReady(snapshot, ACTOR_ID);
        surgeryCase.finalizeSchedule(ACTOR_ID, snapshot.evaluatedAt().plusSeconds(1));

        surgeryCase.invalidateReadiness(
                ACTOR_ID, snapshot.evaluatedAt().plusSeconds(2), "TEAM_ASSIGNMENT_CHANGED");

        assertThat(surgeryCase.getTrangThai()).isEqualTo(SurgeryStatus.PREOP_IN_PROGRESS);
        assertThat(surgeryCase.getAnhChupSanSang()).isNull();
        assertThat(surgeryCase.getThoiDiemSanSang()).isNull();
        assertThat(surgeryCase.getPhienBan()).isEqualTo(4);
    }

    @Test
    void cancel_beforeStart_isAuditedAndPreservesPriorHistory() {
        SurgeryCase surgeryCase = caseInPreop();
        Instant cancelledAt = REQUESTED_AT.plusSeconds(20);

        surgeryCase.cancel(ACTOR_ID, cancelledAt, "PATIENT_REQUEST");

        assertThat(surgeryCase.getTrangThai()).isEqualTo(SurgeryStatus.CANCELLED);
        assertThat(surgeryCase.getNguoiHuy()).isEqualTo(ACTOR_ID);
        assertThat(surgeryCase.getLyDoHuy()).isEqualTo("PATIENT_REQUEST");
        assertThat(surgeryCase.getLichSuTrangThai()).hasSize(3);
    }

    @Test
    void cancel_inProgress_isRejectedAndLeavesCaseUnchanged() {
        SurgeryCase surgeryCase = caseInPreop();
        ReadinessSnapshot snapshot = readiness(surgeryCase, true, true);
        surgeryCase.markReady(snapshot, ACTOR_ID);
        surgeryCase.finalizeSchedule(ACTOR_ID, snapshot.evaluatedAt().plusSeconds(1));
        surgeryCase.start(
                readiness(surgeryCase, true, true, UUID.randomUUID(),
                        snapshot.evaluatedAt().plusSeconds(2)),
                ACTOR_ID,
                snapshot.evaluatedAt().plusSeconds(3));
        long versionBeforeCancel = surgeryCase.getPhienBan();

        assertThatThrownBy(() -> surgeryCase.cancel(
                ACTOR_ID, snapshot.evaluatedAt().plusSeconds(4), "REQUESTED_ABORT"))
                .isInstanceOf(SurgeryRuleException.class)
                .hasFieldOrPropertyWithValue("code", "SURGERY_INVALID_TRANSITION");

        assertThat(surgeryCase.getTrangThai()).isEqualTo(SurgeryStatus.IN_PROGRESS);
        assertThat(surgeryCase.getThoiDiemHuy()).isNull();
        assertThat(surgeryCase.getPhienBan()).isEqualTo(versionBeforeCancel);
    }

    @Test
    void transition_timeBeforePreviousFact_isRejectedWithoutPartialMutation() {
        SurgeryCase surgeryCase = newCase();

        assertThatThrownBy(() -> surgeryCase.beginPreop(ACTOR_ID, REQUESTED_AT.minusSeconds(1)))
                .isInstanceOf(SurgeryRuleException.class)
                .hasFieldOrPropertyWithValue("code", "SURGERY_INVALID_TIME");

        assertThat(surgeryCase.getTrangThai()).isEqualTo(SurgeryStatus.REQUESTED);
        assertThat(surgeryCase.getPhienBan()).isZero();
        assertThat(surgeryCase.getLichSuTrangThai()).hasSize(1);
    }

    private SurgeryCase caseInPreop() {
        SurgeryCase surgeryCase = newCase();
        surgeryCase.beginPreop(ACTOR_ID, REQUESTED_AT.plusSeconds(5));
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
                REQUESTED_AT);
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
        return new ReadinessSnapshot(
                snapshotId,
                surgeryCase.getMaCaPhauThuat(),
                true,
                true,
                true,
                anesthesiaConsentActive,
                true,
                true,
                financialClearanceValid,
                evaluatedAt);
    }
}
