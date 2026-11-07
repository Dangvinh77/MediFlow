package com.mediflow.organization.infrastructure.persistence.adapter;

import com.mediflow.organization.application.port.out.RoomRepository;
import com.mediflow.organization.domain.model.Room;
import com.mediflow.organization.infrastructure.persistence.entity.RoomEntity;
import com.mediflow.organization.infrastructure.persistence.repository.RoomJpaRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class RoomRepositoryAdapter implements RoomRepository {

    private final RoomJpaRepository jpaRepository;

    public RoomRepositoryAdapter(RoomJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Optional<Room> findById(UUID roomId) {
        return jpaRepository.findById(roomId).map(RoomRepositoryAdapter::toDomain);
    }

    @Override
    public Optional<Room> lockById(UUID roomId) {
        return jpaRepository.lockById(roomId).map(RoomRepositoryAdapter::toDomain);
    }

    @Override
    public List<Room> search(UUID departmentId, String roomType, boolean activeOnly) {
        return jpaRepository.search(departmentId, roomType, activeOnly)
                .stream().map(RoomRepositoryAdapter::toDomain).toList();
    }

    @Override
    public Room save(Room room) {
        return toDomain(jpaRepository.saveAndFlush(new RoomEntity(
                room.getRoomId(),
                room.getDepartmentId(),
                room.getRoomType(),
                room.getRoomNumber(),
                room.getRoomName(),
                room.getZone(),
                room.getLocation(),
                room.isActive(),
                room.getCreatedAt(),
                room.getUpdatedAt())));
    }

    @Override
    public boolean existsNumberInZone(String zone, String roomNumber, UUID excludedRoomId) {
        return jpaRepository.existsNumberInZone(zone, roomNumber, excludedRoomId);
    }

    @Override
    public List<Room> findActiveByDepartment(UUID departmentId, UUID excludedRoomId) {
        return jpaRepository.findActiveByDepartment(departmentId, excludedRoomId)
                .stream().map(RoomRepositoryAdapter::toDomain).toList();
    }

    private static Room toDomain(RoomEntity entity) {
        return Room.reconstitute(
                entity.getRoomId(),
                entity.getDepartmentId(),
                entity.getRoomType(),
                entity.getRoomNumber(),
                entity.getRoomName(),
                entity.getZone(),
                entity.getLocation(),
                entity.isActive(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
