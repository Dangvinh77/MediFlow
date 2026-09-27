package com.mediflow.lab.infrastructure.persistence.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.mediflow.lab.infrastructure.persistence.jpaEntity.LabEmergencyOverrideJpaEntity;

public interface LabEmergencyOverrideJpaRepository
        extends JpaRepository<LabEmergencyOverrideJpaEntity, UUID> {
}
