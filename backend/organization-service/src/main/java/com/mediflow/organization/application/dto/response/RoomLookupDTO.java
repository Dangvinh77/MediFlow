package com.mediflow.organization.application.dto.response;

import java.util.UUID;

/** Stable service-to-service projection for an Organization-owned room. */
public record RoomLookupDTO(
        boolean exists,
        boolean active,
        UUID roomId,
        UUID departmentId,
        String roomName,
        String roomType) {

    public static RoomLookupDTO missing(UUID roomId) {
        return new RoomLookupDTO(false, false, roomId, null, null, null);
    }
}
