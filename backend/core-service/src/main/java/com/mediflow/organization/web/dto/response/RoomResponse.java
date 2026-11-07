package com.mediflow.organization.web.dto.response;

import com.mediflow.organization.domain.model.Room;

import java.time.Instant;
import java.util.UUID;

public record RoomResponse(
        UUID roomId,
        UUID departmentId,
        String roomType,
        String roomNumber,
        String roomName,
        String zone,
        String location,
        boolean active,
        Instant createdAt,
        Instant updatedAt) {

    public static RoomResponse from(Room room) {
        return new RoomResponse(room.getRoomId(), room.getDepartmentId(), room.getRoomType(),
                room.getRoomNumber(), room.getRoomName(), room.getZone(), room.getLocation(),
                room.isActive(), room.getCreatedAt(), room.getUpdatedAt());
    }
}
