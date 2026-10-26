package com.mediflow.organization.application.dto.response;

public record ScheduleManagementScopeCommandResult(
        ScheduleManagementScopeSnapshot snapshot,
        boolean replayed,
        int status,
        String location) {}
