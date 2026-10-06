package com.mediflow.organization.infrastructure.persistence.adapter;

import com.mediflow.organization.application.port.out.RoomRepository;
import com.mediflow.organization.domain.model.Room;
import com.mediflow.organization.infrastructure.persistence.entity.RoomEntity;
import com.mediflow.organization.infrastructure.persistence.repository.RoomJpaRepository;
import org.springframework.stereotype.Component;

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
        return jpaRepository.findById(roomId).map(this::toDomain);
    }

    private Room toDomain(RoomEntity entity) {
        return Room.reconstitute(
                entity.getRoomId(),
                entity.getDepartmentId(),
                entity.getRoomName(),
                entity.getRoomType(),
                entity.isActive(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
