package com.mediflow.organization.domain.model;

import java.time.Instant;
import java.util.UUID;

/** Organization-owned operating/procedure room reference. */
public final class Room {

    private final UUID roomId;
    private final UUID departmentId;
    private final String roomName;
    private final String roomType;
    private boolean active;
    private final Instant createdAt;
    private final Instant updatedAt;

    private Room(UUID roomId, UUID departmentId, String roomName, String roomType,
                 boolean active, Instant createdAt, Instant updatedAt) {
        validate(roomId, departmentId, roomName, roomType);
        this.roomId = roomId;
        this.departmentId = departmentId;
        this.roomName = roomName;
        this.roomType = roomType;
        this.active = active;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static Room create(UUID roomId, UUID departmentId, String roomName, String roomType) {
        Instant now = Instant.now();
        return new Room(roomId, departmentId, roomName, roomType, true, now, now);
    }

    public static Room reconstitute(UUID roomId, UUID departmentId, String roomName,
                                    String roomType, boolean active, Instant createdAt,
                                    Instant updatedAt) {
        return new Room(roomId, departmentId, roomName, roomType, active, createdAt, updatedAt);
    }

    private static void validate(UUID roomId, UUID departmentId, String roomName, String roomType) {
        if (roomId == null || departmentId == null) {
            throw new IllegalArgumentException("Room and department IDs are required");
        }
        if (roomName == null || roomName.isBlank() || roomName.length() > 100) {
            throw new IllegalArgumentException("Room name must contain 1 to 100 characters");
        }
        if (roomType == null || roomType.isBlank() || roomType.length() > 30) {
            throw new IllegalArgumentException("Room type must contain 1 to 30 characters");
        }
    }

    public UUID getRoomId() {
        return roomId;
    }

    public UUID getDepartmentId() {
        return departmentId;
    }

    public String getRoomName() {
        return roomName;
    }

    public String getRoomType() {
        return roomType;
    }

    public boolean isActive() {
        return active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
