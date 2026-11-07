package com.mediflow.organization.domain.model;

import com.mediflow.organization.domain.exception.InvalidRoomException;

import java.time.Instant;
import java.util.UUID;

/** Organization-owned workplace room: number, zone, location and type. */
public final class Room {

    private final UUID roomId;
    private final UUID departmentId;
    private final RoomType roomType;
    private String roomNumber;
    private String roomName;
    private String zone;
    private String location;
    private boolean active;
    private final Instant createdAt;
    private Instant updatedAt;

    private Room(UUID roomId, UUID departmentId, RoomType roomType, String roomNumber,
                 String roomName, String zone, String location, boolean active,
                 Instant createdAt, Instant updatedAt) {
        if (roomId == null || departmentId == null) {
            throw new InvalidRoomException("Room and department IDs are required");
        }
        if (roomType == null) {
            throw new InvalidRoomException("Room type is required");
        }
        this.roomId = roomId;
        this.departmentId = departmentId;
        this.roomType = roomType;
        this.roomNumber = required(roomNumber, 20, "Room number");
        this.roomName = required(roomName, 100, "Room name");
        this.zone = required(zone, 50, "Zone");
        this.location = required(location, 255, "Location");
        this.active = active;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static Room create(UUID roomId, UUID departmentId, RoomType roomType, String roomNumber,
                              String roomName, String zone, String location) {
        Instant now = Instant.now();
        return new Room(roomId, departmentId, roomType, roomNumber, roomName, zone, location,
                true, now, now);
    }

    public static Room reconstitute(UUID roomId, UUID departmentId, RoomType roomType,
                                    String roomNumber, String roomName, String zone,
                                    String location, boolean active, Instant createdAt,
                                    Instant updatedAt) {
        return new Room(roomId, departmentId, roomType, roomNumber, roomName, zone, location,
                active, createdAt, updatedAt);
    }

    /** Department and type are fixed: sessions and history reference the room by identity. */
    public void update(String roomNumber, String roomName, String zone, String location) {
        this.roomNumber = required(roomNumber, 20, "Room number");
        this.roomName = required(roomName, 100, "Room name");
        this.zone = required(zone, 50, "Zone");
        this.location = required(location, 255, "Location");
        this.updatedAt = Instant.now();
    }

    public void activate() {
        this.active = true;
        this.updatedAt = Instant.now();
    }

    public void deactivate() {
        this.active = false;
        this.updatedAt = Instant.now();
    }

    private static String required(String value, int max, String field) {
        String trimmed = value == null ? null : value.trim();
        if (trimmed == null || trimmed.isEmpty() || trimmed.length() > max) {
            throw new InvalidRoomException(field + " must contain 1 to " + max + " characters");
        }
        return trimmed;
    }

    public UUID getRoomId() { return roomId; }
    public UUID getDepartmentId() { return departmentId; }
    public RoomType getRoomType() { return roomType; }
    public String getRoomNumber() { return roomNumber; }
    public String getRoomName() { return roomName; }
    public String getZone() { return zone; }
    public String getLocation() { return location; }
    public boolean isActive() { return active; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
