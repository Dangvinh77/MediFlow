package com.mediflow.patient.infrastructure.persistence;

import com.mediflow.patient.domain.model.Patient;
import org.springframework.stereotype.Component;

@Component
public class PatientPersistenceMapper {

    public Patient toDomain(PatientJpaEntity entity) {
        return Patient.restore(entity.getPatientId(), entity.getFullName(), entity.getDateOfBirth(),
                entity.getGender(), entity.getIdentityNumber(), entity.getAddress(),
                entity.getPhoneNumber(), entity.getEmail(), entity.getHealthInsuranceNumber(),
                entity.getCreatedAt(), entity.getUpdatedAt());
    }
}
