package com.mediflow.clinical.infrastructure.persistence.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.mediflow.clinical.domain.model.AppointmentStatus;
import com.mediflow.clinical.infrastructure.persistence.jpaEntity.AppointmentJpaEntity;

import jakarta.persistence.LockModeType;

public interface AppointmentJpaRepository extends JpaRepository<AppointmentJpaEntity, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM AppointmentJpaEntity a WHERE a.appointmentId = :id")
    Optional<AppointmentJpaEntity> findByIdForUpdate(@Param("id") UUID id);
    List<AppointmentJpaEntity> findByPatientIdOrderByAppointmentDateDescAppointmentTimeDesc(UUID patientId);
    boolean existsByPatientIdAndAppointmentDateAndStatus(UUID patientId, LocalDate date, AppointmentStatus status);
    boolean existsByPatientIdAndAppointmentDateAndStatusAndAppointmentIdNot(
            UUID patientId, LocalDate date, AppointmentStatus status, UUID id);
    @Query("""
            SELECT a FROM AppointmentJpaEntity a
            WHERE (:departmentId IS NULL OR a.departmentId = :departmentId)
              AND (:date IS NULL OR a.appointmentDate = :date)
            """)
    Page<AppointmentJpaEntity> search(@Param("departmentId") UUID departmentId,
                                      @Param("date") LocalDate date, Pageable pageable);
}
