package com.mediflow.organization.application.dto.response;

import java.util.UUID;

public record RoomSummary(UUID roomId, UUID departmentId, String roomName, String roomType) {}
