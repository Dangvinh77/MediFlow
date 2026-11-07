package com.mediflow.organization.application.port.out;

import com.mediflow.organization.domain.model.Room;
import com.mediflow.organization.domain.model.RoomType;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RoomRepository {

    Optional<Room> findById(UUID roomId);

    /** Locks the room row for a status or detail change. */
    Optional<Room> lockById(UUID roomId);

    /** Filters are optional; results are ordered by zone, room number and id. */
    List<Room> search(UUID departmentId, RoomType roomType, boolean activeOnly);

    Room save(Room room);

    boolean existsNumberInZone(String zone, String roomNumber, UUID excludedRoomId);

    /** Active rooms of the department other than {@code excludedRoomId}. */
    List<Room> findActiveByDepartment(UUID departmentId, UUID excludedRoomId);
}
