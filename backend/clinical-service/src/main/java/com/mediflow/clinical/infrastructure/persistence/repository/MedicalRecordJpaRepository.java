package com.mediflow.clinical.infrastructure.persistence.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.mediflow.clinical.infrastructure.persistence.jpaEntity.MedicalRecordJpaEntity;

import jakarta.persistence.LockModeType;

public interface MedicalRecordJpaRepository extends JpaRepository<MedicalRecordJpaEntity, UUID> {
    @EntityGraph(attributePaths = "diagnoses")
    @Query("SELECT r FROM MedicalRecordJpaEntity r WHERE r.recordId = :id")
    Optional<MedicalRecordJpaEntity> findAggregateById(@Param("id") UUID id);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM MedicalRecordJpaEntity r WHERE r.recordId = :id")
    Optional<MedicalRecordJpaEntity> findByIdForUpdate(@Param("id") UUID id);
    @EntityGraph(attributePaths = "diagnoses")
    List<MedicalRecordJpaEntity> findByPatientIdOrderByExaminationDateDesc(UUID patientId);
    @EntityGraph(attributePaths = "diagnoses")
    Optional<MedicalRecordJpaEntity> findByAppointmentAppointmentId(UUID appointmentId);
}
