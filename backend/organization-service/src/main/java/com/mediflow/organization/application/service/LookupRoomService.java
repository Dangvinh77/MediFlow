package com.mediflow.organization.application.service;

import com.mediflow.organization.application.dto.response.RoomLookupDTO;
import com.mediflow.organization.application.port.in.LookupRoomUseCase;
import com.mediflow.organization.application.port.out.RoomRepository;
import com.mediflow.organization.domain.model.Room;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** Read-only room projection; absence is not treated as an infrastructure failure. */
@Transactional(readOnly = true)
public class LookupRoomService implements LookupRoomUseCase {

    private final RoomRepository roomRepository;

    public LookupRoomService(RoomRepository roomRepository) {
        this.roomRepository = roomRepository;
    }

    @Override
    public RoomLookupDTO lookup(UUID roomId) {
        return roomRepository.findById(roomId)
                .map(this::toDto)
                .orElseGet(() -> RoomLookupDTO.missing(roomId));
    }

    private RoomLookupDTO toDto(Room room) {
        return new RoomLookupDTO(
                true,
                room.isActive(),
                room.getRoomId(),
                room.getDepartmentId(),
                room.getRoomName(),
                room.getRoomType());
    }
}
