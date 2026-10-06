package com.mediflow.organization.application.port.in;

import com.mediflow.organization.application.dto.response.RoomLookupDTO;

import java.util.UUID;

public interface LookupRoomUseCase {

    RoomLookupDTO lookup(UUID roomId);
}
