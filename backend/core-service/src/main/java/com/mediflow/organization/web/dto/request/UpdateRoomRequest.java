package com.mediflow.organization.web.dto.request;

import com.mediflow.organization.application.port.in.ManageRoomsUseCase.RoomPlacement;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Updates where a room is and what it is called; department and type stay fixed. */
public record UpdateRoomRequest(
        @NotBlank @Size(max = 20) String roomNumber,
        @NotBlank @Size(max = 100) String roomName,
        @NotBlank @Size(max = 50) String zone,
        @NotBlank @Size(max = 255) String location) {

    public RoomPlacement placement() {
        return new RoomPlacement(roomNumber, roomName, zone, location);
    }
}
