package com.mediflow.organization.application.dto.request;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/** Complete replacement of one manager account's schedule-management department scope. */
public record ScheduleManagementScopeCommand(
        @NotNull @PositiveOrZero Long expectedRevision,
        @NotNull @Size(max = 100) List<@NotNull UUID> departmentIds,
        @NotBlank @Size(min = 1, max = 500) String reason) {

    @JsonAnySetter
    public void rejectUnknownField(String name, JsonNode ignored) {
        throw new IllegalArgumentException("Unknown request field: " + name);
    }
}
