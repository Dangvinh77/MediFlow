package com.mediflow.organization.application.dto.response;

import java.time.Instant;
import java.util.UUID;

public record OperatingRoomDTO(UUID roomId, String roomCode, String roomName, UUID departmentId,
                               boolean active, long revision, Instant updatedAt) {}
