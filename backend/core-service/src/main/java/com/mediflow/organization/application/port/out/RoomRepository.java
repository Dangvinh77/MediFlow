package com.mediflow.organization.application.port.out;

import com.mediflow.organization.domain.model.Room;

import java.util.Optional;
import java.util.UUID;

public interface RoomRepository {

    Optional<Room> findById(UUID roomId);
}
