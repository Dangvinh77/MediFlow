package com.mediflow.clinical.infrastructure.persistence.adapter;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.mediflow.clinical.application.port.out.ExamClearanceRepositoryPort;
import com.mediflow.clinical.domain.model.ExamClearance;
import com.mediflow.clinical.infrastructure.persistence.jpaEntity.ExamClearanceJpaEntity;
import com.mediflow.clinical.infrastructure.persistence.repository.ExamClearanceJpaRepository;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class ExamClearancePersistenceAdapter implements ExamClearanceRepositoryPort {
    private final ExamClearanceJpaRepository repository;

    @Override
    @Transactional
    public boolean claimAndSave(ExamClearance clearance) {
        return repository.insertIfEventNotClaimed(clearance.getClearanceId(), clearance.getEventId(),
                clearance.getInvoiceId(), clearance.getAccountId(), clearance.getAppointmentId(),
                clearance.getRecordId(), clearance.getPatientId(), clearance.getCareEpisodeType().name(),
                clearance.getCareEpisodeId(), clearance.getAmount(), clearance.getCurrency(),
                clearance.getExpiresAt(), clearance.isEmergencyOverride(), clearance.getGrantedAt()) == 1;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ExamClearance> findByAppointment(UUID appointmentId) {
        return repository.findFirstByAppointmentIdOrderByGrantedAtDesc(appointmentId).map(this::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ExamClearance> findByRecord(UUID recordId) {
        return repository.findFirstByRecordIdOrderByGrantedAtDesc(recordId).map(this::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ExamClearance> findValidForAppointment(UUID appointmentId, Instant at) {
        return repository.findValidForAppointment(appointmentId, at).stream().findFirst().map(this::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ExamClearance> findValidForRecord(UUID recordId, Instant at) {
        return repository.findValidForRecord(recordId, at).stream().findFirst().map(this::toDomain);
    }

    private ExamClearance toDomain(ExamClearanceJpaEntity entity) {
        return ExamClearance.restore(entity.getClearanceId(), entity.getEventId(), entity.getInvoiceId(),
                entity.getAccountId(), entity.getAppointmentId(), entity.getRecordId(), entity.getPatientId(),
                entity.getCareEpisodeType(), entity.getCareEpisodeId(), entity.getAmount(), entity.getCurrency(),
                entity.getExpiresAt(), entity.isEmergencyOverride(), entity.getGrantedAt());
    }
}
