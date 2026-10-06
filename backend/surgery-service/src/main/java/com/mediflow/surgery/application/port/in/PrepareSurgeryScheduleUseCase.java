package com.mediflow.surgery.application.port.in;

import com.mediflow.surgery.application.dto.SurgeryCommandOutcome;
import com.mediflow.surgery.domain.model.SurgeryAuditActor;
import com.mediflow.surgery.domain.model.SurgeryTeamRole;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

/** Creates or revises a non-reserving schedule draft while a case is in pre-op. */
public interface PrepareSurgeryScheduleUseCase {

    SurgeryCommandOutcome prepare(Command command);

    record TeamMember(UUID staffId, SurgeryTeamRole role) {
        public TeamMember {
            if (staffId == null || role == null) {
                throw new IllegalArgumentException("A schedule team member requires staff and role");
            }
        }
    }

    record Command(UUID surgeryCaseId, long expectedCaseRevision, long expectedScheduleRevision,
                   UUID roomId, Instant startsAt, Instant endsAt, List<TeamMember> team,
                   String idempotencyKey, SurgeryAuditActor actor, String correlationId) {
        public static Command fromRequest(UUID caseId,
                com.mediflow.surgery.application.dto.request.PrepareSurgeryScheduleRequest request,
                String key, com.mediflow.surgery.application.dto.SurgeryActorIdentity identity, String correlation) {
            return new Command(caseId,request.expectedCaseRevision(),request.expectedScheduleRevision(),
                    request.roomId(),request.startsAt(),request.endsAt(),request.team().stream()
                    .map(member -> new TeamMember(member.staffId(),member.role())).toList(),key,
                    SurgeryAuditActor.human(identity.accountId(),identity.verifiedStaffId()),correlation);
        }
        public Command {
            if (surgeryCaseId == null || expectedCaseRevision < 0 || expectedScheduleRevision < 0
                    || roomId == null || startsAt == null || endsAt == null || !endsAt.isAfter(startsAt)
                    || team == null || team.isEmpty() || team.stream().anyMatch(java.util.Objects::isNull)
                    || new HashSet<>(team.stream().map(TeamMember::staffId).toList()).size() != team.size()
                    || idempotencyKey == null || idempotencyKey.isBlank() || actor == null
                    || correlationId == null || correlationId.isBlank()) {
                throw new IllegalArgumentException("Prepare surgery schedule command is invalid");
            }
            team = List.copyOf(team);
        }
    }
}
