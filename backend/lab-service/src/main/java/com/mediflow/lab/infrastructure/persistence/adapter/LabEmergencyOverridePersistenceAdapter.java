package com.mediflow.lab.infrastructure.persistence.adapter;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.mediflow.lab.application.port.out.LabEmergencyOverrideRepositoryPort;
import com.mediflow.lab.domain.model.LabEmergencyOverride;
import com.mediflow.lab.infrastructure.persistence.jpaEntity.LabEmergencyOverrideJpaEntity;
import com.mediflow.lab.infrastructure.persistence.repository.LabEmergencyOverrideJpaRepository;
import com.mediflow.lab.infrastructure.persistence.repository.LabTestJpaRepository;

import lombok.RequiredArgsConstructor;

/** Persists the complete emergency approval audit beside the local test. */
@Component
@RequiredArgsConstructor
public class LabEmergencyOverridePersistenceAdapter implements LabEmergencyOverrideRepositoryPort {

    private final LabEmergencyOverrideJpaRepository overrides;
    private final LabTestJpaRepository tests;

    @Override
    @Transactional
    public void save(LabEmergencyOverride value) {
        overrides.saveAndFlush(LabEmergencyOverrideJpaEntity.builder()
                .overrideId(value.overrideId())
                .test(tests.getReferenceById(value.testId()))
                .patientId(value.patientId())
                .careEpisodeType(value.careEpisodeType())
                .careEpisodeId(value.careEpisodeId())
                .approvedBy(value.approvedBy())
                .approverRole(value.approverRole())
                .reason(value.reason())
                .approvedAt(value.approvedAt())
                .build());
    }
}
