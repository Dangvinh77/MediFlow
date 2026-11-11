package com.mediflow.clinical.application.port.out;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.mediflow.clinical.domain.model.PatientAllergy;

/** Allergies are added and change status; there is deliberately no delete. */
public interface PatientAllergyRepositoryPort {
    void insert(PatientAllergy allergy);

    Optional<PatientAllergy> findByIdForUpdate(UUID allergyId);

    void changeStatus(PatientAllergy allergy);

    List<PatientAllergy> findByPatient(UUID patientId);

    Optional<PatientAllergy> findActiveByKey(UUID patientId, String substanceKey);
}
