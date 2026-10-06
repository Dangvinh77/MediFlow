package com.mediflow.surgery.application.dto.request;

import com.mediflow.surgery.domain.model.SurgeryTeamRole;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** This requests a draft, never READY or a finalized booking. */
public record PrepareSurgeryScheduleRequest(
        @NotNull @PositiveOrZero Long expectedCaseRevision,
        @NotNull @PositiveOrZero Long expectedScheduleRevision,
        @NotNull UUID roomId, @NotNull Instant startsAt, @NotNull Instant endsAt,
        @NotEmpty @Size(max = 32) List<@NotNull @Valid TeamMember> team) {
    public record TeamMember(@NotNull UUID staffId, @NotNull SurgeryTeamRole role) {
        @com.fasterxml.jackson.annotation.JsonAnySetter
        public void rejectUnknownField(String name, Object value) {
            throw new IllegalArgumentException("Unexpected Surgery team field");
        }
    }
    @com.fasterxml.jackson.annotation.JsonAnySetter
    public void rejectUnknownField(String name, Object value) {
        throw new IllegalArgumentException("Unexpected Surgery command field");
    }
    @AssertTrue(message = "endsAt must be after startsAt")
    public boolean isIntervalValid() { return startsAt == null || endsAt == null || endsAt.isAfter(startsAt); }
    @AssertTrue(message = "A staff member may have only one role in a draft")
    public boolean isTeamUnique() {
        return team == null || team.stream().anyMatch(java.util.Objects::isNull)
                || team.stream().map(TeamMember::staffId).distinct().count() == team.size();
    }
}
