package com.mediflow.organization.infrastructure.persistence.repository;

import com.mediflow.organization.infrastructure.persistence.entity.RoomEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface RoomJpaRepository extends JpaRepository<RoomEntity, UUID> {
}
