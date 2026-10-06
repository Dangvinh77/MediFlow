package com.mediflow.organization.domain.model;

import com.mediflow.common.exception.BusinessRuleException;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

public record OperatingRoom(UUID roomId, String roomCode, String roomName, UUID departmentId,
                            boolean active, long revision, Instant updatedAt) {
    public OperatingRoom {
        if (roomId == null || departmentId == null || updatedAt == null || revision < 1
                || roomCode == null || !roomCode.matches("[A-Za-z0-9_-]{1,40}")
                || roomName == null || roomName.isBlank() || roomName.length() > 100) {
            throw new BusinessRuleException("ORG_ROOM_INVALID", "Invalid operating-room reference");
        }
        roomCode = roomCode.toUpperCase(Locale.ROOT);
        roomName = roomName.trim();
    }
}
