package com.mediflow.clinical.infrastructure.persistence.adapter;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import com.mediflow.clinical.application.port.out.AppointmentRepositoryPort;
import com.mediflow.clinical.domain.model.Appointment;
import com.mediflow.clinical.domain.model.AppointmentStatus;
import com.mediflow.clinical.domain.exception.DuplicatePendingAppointmentException;
import com.mediflow.clinical.infrastructure.persistence.jpaEntity.AppointmentJpaEntity;
import com.mediflow.clinical.infrastructure.persistence.repository.AppointmentJpaRepository;
import com.mediflow.common.api.PageQuery;
import com.mediflow.common.api.PageResult;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class AppointmentPersistenceAdapter implements AppointmentRepositoryPort {
    private final AppointmentJpaRepository repository;

    @Override public Appointment save(Appointment value) {
        try {
            return toDomain(repository.saveAndFlush(toEntity(value)));
        } catch (DataIntegrityViolationException ex) {
            if (hasConstraint(ex, "uq_appointment_pending_patient_date")) {
                throw new DuplicatePendingAppointmentException(
                        "Patient already has a pending appointment on this date");
            }
            throw ex;
        }
    }
    @Override public Optional<Appointment> findById(UUID id) { return repository.findById(id).map(this::toDomain); }
    @Override public Optional<Appointment> findByIdForUpdate(UUID id) { return repository.findByIdForUpdate(id).map(this::toDomain); }
    @Override public List<Appointment> findByPatient(UUID patientId) {
        return repository.findByPatientIdOrderByAppointmentDateDescAppointmentTimeDesc(patientId)
                .stream().map(this::toDomain).toList();
    }
    @Override public PageResult<Appointment> search(UUID departmentId, LocalDate date, PageQuery query) {
        Page<AppointmentJpaEntity> page = repository.search(departmentId, date, PageRequest.of(query.page(),
                query.size(), Sort.by(Sort.Direction.ASC, "appointmentDate", "appointmentTime")));
        return PageResult.of(page.getContent().stream().map(this::toDomain).toList(),
                page.getTotalElements(), query.page(), query.size());
    }
    @Override public boolean existsPendingSameDay(UUID patientId, LocalDate date) {
        return repository.existsByPatientIdAndAppointmentDateAndStatus(patientId, date, AppointmentStatus.PENDING);
    }
    @Override public boolean existsPendingSameDayExcludingId(UUID patientId, LocalDate date, UUID excludedId) {
        return repository.existsByPatientIdAndAppointmentDateAndStatusAndAppointmentIdNot(
                patientId, date, AppointmentStatus.PENDING, excludedId);
    }

    private Appointment toDomain(AppointmentJpaEntity e) {
        return Appointment.restore(e.getAppointmentId(), e.getPatientId(), e.getDoctorId(), e.getDepartmentId(),
                e.getAppointmentDate(), e.getAppointmentTime(), e.getStatus(), e.getReason(),
                e.getCreatedAt(), e.getUpdatedAt());
    }
    private AppointmentJpaEntity toEntity(Appointment a) {
        return AppointmentJpaEntity.builder().appointmentId(a.getAppointmentId()).patientId(a.getPatientId())
                .doctorId(a.getDoctorId()).departmentId(a.getDepartmentId())
                .appointmentDate(a.getAppointmentDate()).appointmentTime(a.getAppointmentTime())
                .status(a.getStatus()).reason(a.getReason()).createdAt(a.getCreatedAt())
                .updatedAt(a.getUpdatedAt()).build();
    }

    private static boolean hasConstraint(Throwable error, String constraint) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof org.hibernate.exception.ConstraintViolationException violation
                    && constraint.equalsIgnoreCase(violation.getConstraintName())) {
                return true;
            }
        }
        return false;
    }
}
