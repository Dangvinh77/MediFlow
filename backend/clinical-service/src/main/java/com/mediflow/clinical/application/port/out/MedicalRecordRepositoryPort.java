package com.mediflow.clinical.application.port.out;

import java.util.UUID;
import java.util.List;
import java.util.Optional;
import com.mediflow.clinical.domain.model.MedicalRecord;

public interface MedicalRecordRepositoryPort {
    MedicalRecord save(MedicalRecord record);
    Optional<MedicalRecord> findById(UUID id);
    Optional<MedicalRecord> findByIdForUpdate(UUID id);
    List<MedicalRecord> findByPatient(UUID patientId);

    /** BR-R2: detect an already recorded appointment, backed by a unique database constraint. */
    Optional<MedicalRecord> findByAppointmentId(UUID appointmentId);
}
