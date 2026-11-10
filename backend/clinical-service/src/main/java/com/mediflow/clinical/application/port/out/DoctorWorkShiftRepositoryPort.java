package com.mediflow.clinical.application.port.out;
import java.time.*;
import java.util.*;
import com.mediflow.clinical.domain.model.DoctorWorkShift;
import com.mediflow.common.api.*;
public interface DoctorWorkShiftRepositoryPort {
    Optional<DoctorWorkShift> findById(UUID id);
    Optional<DoctorWorkShift> findByIdForUpdate(UUID id);
    DoctorWorkShift insert(DoctorWorkShift shift, UUID actor, Instant at);
    void update(DoctorWorkShift before, DoctorWorkShift after, UUID actor, String reason, Instant at);
    PageResult<DoctorWorkShift> search(UUID department, LocalDate from, LocalDate to, PageQuery page);
    List<DoctorWorkShift> findByDoctor(UUID doctor, LocalDate from, LocalDate to);
    void linkSession(UUID shiftId, UUID sessionId);
    List<UUID> linkedSessionIds(UUID shiftId);
    Optional<DoctorWorkShift> findBySession(UUID sessionId);
    void cancelKeepingStoppedSessions(DoctorWorkShift before, DoctorWorkShift after, UUID actor, String reason, Instant at);
}
