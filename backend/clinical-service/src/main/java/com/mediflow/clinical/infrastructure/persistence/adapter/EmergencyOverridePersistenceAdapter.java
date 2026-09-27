package com.mediflow.clinical.infrastructure.persistence.adapter;

import org.springframework.stereotype.Component;

import com.mediflow.clinical.application.port.out.EmergencyOverrideRepositoryPort;
import com.mediflow.clinical.domain.model.EmergencyOverride;
import com.mediflow.clinical.infrastructure.persistence.jpaEntity.ClinicalEmergencyOverrideJpaEntity;
import com.mediflow.clinical.infrastructure.persistence.repository.ClinicalEmergencyOverrideJpaRepository;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class EmergencyOverridePersistenceAdapter implements EmergencyOverrideRepositoryPort {
    private final ClinicalEmergencyOverrideJpaRepository repository;

    @Override
    public EmergencyOverride save(EmergencyOverride override) {
        repository.save(ClinicalEmergencyOverrideJpaEntity.builder()
                .overrideId(override.getOverrideId())
                .appointmentId(override.getAppointmentId())
                .recordId(override.getRecordId())
                .patientId(override.getPatientId())
                .careEpisodeId(override.getCareEpisodeId())
                .approvedBy(override.getApprovedBy())
                .approverRole(override.getApproverRole())
                .reason(override.getReason())
                .approvedAt(override.getApprovedAt())
                .build());
        return override;
    }
}
