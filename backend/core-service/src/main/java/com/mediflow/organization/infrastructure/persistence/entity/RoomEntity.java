package com.mediflow.organization.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "room")
public class RoomEntity {

    @Id
    @Column(name = "room_id", nullable = false, updatable = false)
    private UUID roomId;

    @Column(name = "department_id", nullable = false, updatable = false)
    private UUID departmentId;

    @Column(name = "room_type", nullable = false, length = 30, updatable = false)
    private String roomType;

    @Column(name = "room_number", nullable = false, length = 20)
    private String roomNumber;

    @Column(name = "room_name", nullable = false, length = 100)
    private String roomName;

    @Column(name = "zone", nullable = false, length = 50)
    private String zone;

    @Column(name = "location", nullable = false, length = 255)
    private String location;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    protected RoomEntity() {
    }

    public RoomEntity(UUID roomId, UUID departmentId, String roomType, String roomNumber,
                      String roomName, String zone, String location, boolean active,
                      Instant createdAt, Instant updatedAt) {
        this.roomId = roomId;
        this.departmentId = departmentId;
        this.roomType = roomType;
        this.roomNumber = roomNumber;
        this.roomName = roomName;
        this.zone = zone;
        this.location = location;
        this.active = active;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public UUID getRoomId() { return roomId; }
    public UUID getDepartmentId() { return departmentId; }
    public String getRoomType() { return roomType; }
    public String getRoomNumber() { return roomNumber; }
    public String getRoomName() { return roomName; }
    public String getZone() { return zone; }
    public String getLocation() { return location; }
    public boolean isActive() { return active; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
