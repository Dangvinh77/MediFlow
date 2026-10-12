package com.mediflow.surgery.domain.model;

import com.mediflow.surgery.domain.exception.SurgeryRuleException;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** Immutable observed evidence. Construction does not grant authority or change the case lifecycle. */
public record SurgerySafetyPause(UUID pauseId, UUID commandId, UUID surgeryCaseId,
        long expectedCaseRevision, UUID scheduleId, long scheduleRevision,
        SurgerySafetyPausePolicy policy, SurgerySafetyPhase phase, Instant observedAt, Instant recordedAt,
        SurgeryAuditActor recorder, String correlationId, List<Participant> participants, List<Decision> decisions) {
    public SurgerySafetyPause {
        if (pauseId == null || commandId == null || surgeryCaseId == null || expectedCaseRevision < 0
                || scheduleId == null || scheduleRevision < 0 || policy == null || phase == null
                || observedAt == null || recordedAt == null || observedAt.isAfter(recordedAt)
                || recorder == null || recorder.actorType() != SurgeryActorType.HUMAN
                || recorder.verifiedStaffId() == null || correlationId == null || correlationId.isBlank()
                || correlationId.length() > 160 || participants == null || participants.isEmpty()
                || participants.size() > 100 || participants.stream().anyMatch(java.util.Objects::isNull)
                || decisions == null || decisions.isEmpty() || decisions.size() > 100
                || decisions.stream().anyMatch(java.util.Objects::isNull)) throw invalid();
        participants = participants.stream().sorted(Comparator.comparing(Participant::staffId)).toList();
        decisions = decisions.stream().sorted(Comparator.comparing(Decision::itemCode)).toList();
        if (participants.stream().map(Participant::staffId).distinct().count() != participants.size()
                || decisions.stream().map(Decision::itemCode).distinct().count() != decisions.size()) throw invalid();
        var rule = policy.rule(phase);
        if (!participants.stream().map(Participant::role).collect(java.util.stream.Collectors.toSet())
                .containsAll(rule.requiredParticipantRoles())
                || !decisions.stream().map(Decision::itemCode).toList()
                .equals(rule.items().stream().map(SurgerySafetyPausePolicy.ItemRule::itemCode).toList())) throw invalid();
        for (int index = 0; index < decisions.size(); index++) {
            var decision = decisions.get(index);
            if (decision.status() == SurgeryChecklistStatus.NOT_APPLICABLE
                    && !rule.items().get(index).permittedNotApplicableReasons().contains(decision.reasonCode())) {
                throw invalid();
            }
        }
    }

    private static SurgeryRuleException invalid() {
        return new SurgeryRuleException("SURGERY_SAFETY_PAUSE_INVALID", "Invalid safety pause evidence");
    }

    /** Safety evidence is not a command to advance induction, incision, exit or Billing state. */
    public void validateAppend(SurgeryStatus status, long caseRevision, String procedureCode,
            UUID currentScheduleId, long currentScheduleRevision, SurgerySafetyPause previous) {
        if (caseRevision != expectedCaseRevision || !policy.procedureCode().equals(procedureCode)
                || !scheduleId.equals(currentScheduleId) || scheduleRevision != currentScheduleRevision) {
            throw new SurgeryRuleException("SURGERY_SAFETY_CONTEXT_MISMATCH", "Safety pause context has changed");
        }
        if (phase == SurgerySafetyPhase.BEFORE_ANAESTHESIA
                ? status != SurgeryStatus.SCHEDULED && status != SurgeryStatus.IN_PROGRESS
                : status != SurgeryStatus.IN_PROGRESS) {
            throw new SurgeryRuleException("SURGERY_SAFETY_PHASE_STATE", "Case state does not permit this safety pause");
        }
        if (previous == null ? phase.previous() != null : phase.previous() != previous.phase()
                || !surgeryCaseId.equals(previous.surgeryCaseId()) || !policy.equals(previous.policy())
                || !scheduleId.equals(previous.scheduleId()) || scheduleRevision != previous.scheduleRevision()
                || observedAt.isBefore(previous.observedAt())) {
            throw new SurgeryRuleException("SURGERY_SAFETY_PHASE_ORDER", "Safety pause order or pinned policy has changed");
        }
    }

    public record Participant(UUID staffId, SurgeryTeamRole role, UUID authorityReferenceId, long authorityRevision) {
        public Participant {
            if (staffId == null || role == null || authorityReferenceId == null || authorityRevision < 0) throw invalid();
        }
    }

    /** N/A requires its own applicability evidence; it cannot be a renamed failed observation. */
    public record Decision(String itemCode, SurgeryChecklistStatus status, UUID evidenceReferenceId,
            long evidenceRevision, String reasonCode, UUID applicabilityEvidenceId) {
        public Decision {
            if (itemCode == null || !itemCode.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,63}")
                    || evidenceReferenceId == null || evidenceRevision < 0
                    || status != SurgeryChecklistStatus.SATISFIED && status != SurgeryChecklistStatus.NOT_APPLICABLE
                    || status == SurgeryChecklistStatus.SATISFIED && (reasonCode != null || applicabilityEvidenceId != null)
                    || status == SurgeryChecklistStatus.NOT_APPLICABLE && (applicabilityEvidenceId == null
                        || reasonCode == null || !reasonCode.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,63}"))) throw invalid();
        }
    }
}
