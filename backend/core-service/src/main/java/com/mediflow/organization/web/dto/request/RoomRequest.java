package com.mediflow.organization.web.dto.request;

import com.mediflow.organization.application.port.in.ManageRoomsUseCase.RoomPlacement;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/** Creates a workplace room. Department and type are immutable afterwards. */
public record RoomRequest(
        @NotNull(message = "Department is required") UUID departmentId,
        @NotBlank(message = "Room type is required") @Size(max = 40) String roomType,
        @NotBlank @Size(max = 20) String roomNumber,
        @NotBlank @Size(max = 100) String roomName,
        @NotBlank @Size(max = 50) String zone,
        @NotBlank @Size(max = 255) String location) {

    public RoomPlacement placement() {
        return new RoomPlacement(roomNumber, roomName, zone, location);
    }
}
