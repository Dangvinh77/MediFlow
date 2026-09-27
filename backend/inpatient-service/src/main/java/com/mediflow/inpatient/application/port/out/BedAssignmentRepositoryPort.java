package com.mediflow.inpatient.application.port.out;

import com.mediflow.inpatient.domain.model.BedAssignment;

import java.util.Optional;
import java.util.UUID;

public interface BedAssignmentRepositoryPort {
    Optional<BedAssignment> findActiveByAdmissionId(UUID admissionId);
    Optional<BedAssignment> findActiveByBedId(UUID bedId);
    BedAssignment save(BedAssignment assignment);
    BedAssignment saveAndFlush(BedAssignment assignment);
}
