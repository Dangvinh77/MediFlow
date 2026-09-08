package com.mediflow.clinical.application.port.out;

import java.util.UUID;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import com.mediflow.common.api.PageQuery;
import com.mediflow.common.api.PageResult;
import com.mediflow.clinical.domain.model.Appointment;

/** BR-A2 also requires database enforcement against concurrent writes in the future persistence adapter. */
public interface AppointmentRepositoryPort {
    Appointment save(Appointment appointment);
    Optional<Appointment> findById(UUID id);
    List<Appointment> findByPatient(UUID patientId);
    PageResult<Appointment> search(UUID departmentId, LocalDate appointmentDate, PageQuery page);
    boolean existsPendingSameDay(UUID patientId, LocalDate appointmentDate);

    /** Updating an appointment must not count that same appointment as a duplicate. */
    boolean existsPendingSameDayExcludingId(UUID patientId, LocalDate appointmentDate, UUID excludedId);
}
