package com.mediflow.clinical.infrastructure.persistence.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.mediflow.clinical.infrastructure.persistence.jpaEntity.ClinicalEmergencyOverrideJpaEntity;

public interface ClinicalEmergencyOverrideJpaRepository
        extends JpaRepository<ClinicalEmergencyOverrideJpaEntity, UUID> {
}
