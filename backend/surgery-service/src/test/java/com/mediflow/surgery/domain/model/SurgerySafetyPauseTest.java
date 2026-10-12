package com.mediflow.surgery.domain.model;

import com.mediflow.surgery.application.service.SurgerySafetyPauseFingerprints;
import com.mediflow.surgery.domain.exception.SurgeryRuleException;
import com.mediflow.surgery.support.SurgerySafetyPauseFixture;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import static com.mediflow.surgery.support.SurgerySafetyPauseFixture.*;
import static org.assertj.core.api.Assertions.*;

class SurgerySafetyPauseTest {
    private final UUID caseId = UUID.randomUUID();
    private final UUID scheduleId = UUID.randomUUID();

    @Test void policy_missingPhase_rejects() {
        assertThatThrownBy(() -> new SurgerySafetyPausePolicy(UUID.randomUUID(), 1, "TEST-PROC", UUID.randomUUID(),
                policy().phases().subList(0, 2))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void policy_emptyParticipantRequirement_rejects() {
        assertThatThrownBy(() -> new SurgerySafetyPausePolicy.PhaseRule(SurgerySafetyPhase.BEFORE_INCISION,
                Set.of(), policy().phases().getFirst().items())).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest @EnumSource(value = SurgeryChecklistStatus.class, names = {"PENDING", "FAILED"})
    void decision_unperformedOrFailed_cannotBecomeCompletedPause(SurgeryChecklistStatus status) {
        assertThatThrownBy(() -> new SurgerySafetyPause.Decision("TEST_ITEM", status, UUID.randomUUID(), 1, null, null))
                .isInstanceOf(SurgeryRuleException.class);
    }

    @Test void decision_notApplicableWithoutIndependentEvidence_rejects() {
        assertThatThrownBy(() -> new SurgerySafetyPause.Decision("TEST_ITEM", SurgeryChecklistStatus.NOT_APPLICABLE,
                UUID.randomUUID(), 1, "TEST_NOT_REQUIRED", null)).isInstanceOf(SurgeryRuleException.class);
    }

    @Test void pause_permittedNotApplicable_preservesSeparateDecisionEvidence() {
        var original = pause(caseId, scheduleId, SurgerySafetyPhase.BEFORE_ANAESTHESIA, 3);
        UUID applicability = UUID.randomUUID();
        var accepted = copy(original, original.pauseId(), original.commandId(), policy(), original.phase(),
                original.observedAt(), original.recordedAt(), List.of(new SurgerySafetyPause.Decision("TEST_ITEM",
                        SurgeryChecklistStatus.NOT_APPLICABLE, UUID.randomUUID(), 4, "TEST_NOT_REQUIRED", applicability)));
        assertThat(accepted.decisions().getFirst().applicabilityEvidenceId()).isEqualTo(applicability);
    }

    @Test void pause_unapprovedNotApplicableReason_rejects() {
        var original = pause(caseId, scheduleId, SurgerySafetyPhase.BEFORE_ANAESTHESIA, 3);
        assertThatThrownBy(() -> copy(original, original.pauseId(), original.commandId(), policy(), original.phase(),
                original.observedAt(), original.recordedAt(), List.of(new SurgerySafetyPause.Decision("TEST_ITEM",
                        SurgeryChecklistStatus.NOT_APPLICABLE, UUID.randomUUID(), 1, "UNKNOWN", UUID.randomUUID()))))
                .isInstanceOf(SurgeryRuleException.class);
    }

    @Test void pause_missingParticipantRole_rejectsWithoutInferringRoleFromRecorder() {
        var original = pause(caseId, scheduleId, SurgerySafetyPhase.BEFORE_ANAESTHESIA, 3);
        assertThatThrownBy(() -> new SurgerySafetyPause(original.pauseId(), original.commandId(), caseId, 3, scheduleId, 1,
                policy(), original.phase(), original.observedAt(), original.recordedAt(), original.recorder(), "test",
                List.of(new SurgerySafetyPause.Participant(UUID.randomUUID(), SurgeryTeamRole.OR_NURSE, UUID.randomUUID(), 1)),
                original.decisions())).isInstanceOf(SurgeryRuleException.class);
    }

    @Test void pause_futureObservation_rejects() {
        var original = pause(caseId, scheduleId, SurgerySafetyPhase.BEFORE_ANAESTHESIA, 3);
        assertThatThrownBy(() -> copy(original, original.pauseId(), original.commandId(), policy(), original.phase(),
                original.recordedAt().plusNanos(1), original.recordedAt(), original.decisions())).isInstanceOf(SurgeryRuleException.class);
    }

    @Test void append_missingEarlierPhase_rejects() {
        assertThatThrownBy(() -> pause(caseId, scheduleId, SurgerySafetyPhase.BEFORE_INCISION, 4)
                .validateAppend(SurgeryStatus.IN_PROGRESS, 4, "TEST-PROC", scheduleId, 1, null))
                .isInstanceOf(SurgeryRuleException.class).extracting("code").isEqualTo("SURGERY_SAFETY_PHASE_ORDER");
    }

    @Test void append_incisionBeforeStart_rejects() {
        var first = pause(caseId, scheduleId, SurgerySafetyPhase.BEFORE_ANAESTHESIA, 3);
        assertThatThrownBy(() -> pause(caseId, scheduleId, SurgerySafetyPhase.BEFORE_INCISION, 3)
                .validateAppend(SurgeryStatus.SCHEDULED, 3, "TEST-PROC", scheduleId, 1, first))
                .isInstanceOf(SurgeryRuleException.class);
    }

    @ParameterizedTest @EnumSource(value = SurgeryStatus.class, names = {"CANCELLED", "COMPLETED", "READY", "REQUESTED", "PREOP_IN_PROGRESS"})
    void append_nonOperativeState_rejects(SurgeryStatus state) {
        assertThatThrownBy(() -> pause(caseId, scheduleId, SurgerySafetyPhase.BEFORE_ANAESTHESIA, 3)
                .validateAppend(state, 3, "TEST-PROC", scheduleId, 1, null)).isInstanceOf(SurgeryRuleException.class);
    }

    @Test void append_changedPinnedPolicy_rejects() {
        var first = pause(caseId, scheduleId, SurgerySafetyPhase.BEFORE_ANAESTHESIA, 3);
        var second = pause(caseId, scheduleId, SurgerySafetyPhase.BEFORE_INCISION, 4);
        var changed = new SurgerySafetyPausePolicy(policy().policyId(), 2, "TEST-PROC", UUID.randomUUID(), policy().phases());
        var value = copy(second, second.pauseId(), second.commandId(), changed, second.phase(), second.observedAt(),
                second.recordedAt(), second.decisions());
        assertThatThrownBy(() -> value.validateAppend(SurgeryStatus.IN_PROGRESS, 4, "TEST-PROC", scheduleId, 1, first))
                .isInstanceOf(SurgeryRuleException.class);
    }

    @Test void append_nanosecondBackwardObservation_rejects() {
        var first = pause(caseId, scheduleId, SurgerySafetyPhase.BEFORE_ANAESTHESIA, 3);
        var second = pause(caseId, scheduleId, SurgerySafetyPhase.BEFORE_INCISION, 4);
        var backward = copy(second, second.pauseId(), second.commandId(), policy(), second.phase(), first.observedAt().minusNanos(1),
                second.recordedAt(), second.decisions());
        assertThatThrownBy(() -> backward.validateAppend(SurgeryStatus.IN_PROGRESS, 4, "TEST-PROC", scheduleId, 1, first))
                .isInstanceOf(SurgeryRuleException.class);
    }

    @Test void fingerprint_retryRecordingTimeAndGeneratedId_returnsSameIntent() {
        var first = pause(caseId, scheduleId, SurgerySafetyPhase.BEFORE_ANAESTHESIA, 3);
        var retry = copy(first, UUID.randomUUID(), first.commandId(), policy(), first.phase(), first.observedAt(),
                first.recordedAt().plusSeconds(10), first.decisions());
        assertThat(SurgerySafetyPauseFingerprints.intent(retry)).isEqualTo(SurgerySafetyPauseFingerprints.intent(first));
    }

    @Test void fingerprint_changedObservedTime_changesIntent() {
        var first = pause(caseId, scheduleId, SurgerySafetyPhase.BEFORE_ANAESTHESIA, 3);
        var changed = copy(first, first.pauseId(), first.commandId(), policy(), first.phase(), first.observedAt().plusNanos(1),
                first.recordedAt(), first.decisions());
        assertThat(SurgerySafetyPauseFingerprints.intent(changed)).isNotEqualTo(SurgerySafetyPauseFingerprints.intent(first));
    }
}
