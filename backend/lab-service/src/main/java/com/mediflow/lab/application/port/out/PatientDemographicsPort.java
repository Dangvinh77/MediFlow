package com.mediflow.lab.application.port.out;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/**
 * Sex and date of birth from Patient, read only to pick a reference range when a result is released.
 * Lab never stores them. Empty when Patient is unreachable or the answer cannot be trusted: the flag then
 * stays UNDETERMINED rather than guessing.
 */
@FunctionalInterface
public interface PatientDemographicsPort {
    Optional<PatientDemographics> find(UUID patientId, String correlationId);

    static PatientDemographicsPort unavailable() {
        return (patientId, correlationId) -> Optional.empty();
    }

    /** {@code sex} uses the reference-range codes MALE / FEMALE. */
    record PatientDemographics(String sex, LocalDate dateOfBirth) {}
}
