package com.mediflow.organization.application.port.in;

import com.mediflow.organization.domain.model.Room;

import java.util.List;
import java.util.UUID;

/** Administration of Organization-owned workplace rooms. */
public interface ManageRoomsUseCase {

    List<Room> list(UUID departmentId, String roomType, boolean activeOnly);

    Room get(UUID roomId);

    Room create(RoomDetails details);

    Room update(UUID roomId, RoomPlacement placement);

    Room changeStatus(UUID roomId, boolean active);

    record RoomDetails(UUID departmentId, String roomType, RoomPlacement placement) {}

    record RoomPlacement(String roomNumber, String roomName, String zone, String location) {}
}
