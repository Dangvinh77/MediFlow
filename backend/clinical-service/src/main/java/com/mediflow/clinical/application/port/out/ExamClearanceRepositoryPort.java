package com.mediflow.clinical.application.port.out;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import com.mediflow.clinical.domain.model.ExamClearance;

public interface ExamClearanceRepositoryPort {
    boolean claimAndSave(ExamClearance clearance);
    Optional<ExamClearance> findByAppointment(UUID appointmentId);
    Optional<ExamClearance> findByRecord(UUID recordId);
    Optional<ExamClearance> findValidForAppointment(UUID appointmentId, Instant at);
    Optional<ExamClearance> findValidForRecord(UUID recordId, Instant at);
}
