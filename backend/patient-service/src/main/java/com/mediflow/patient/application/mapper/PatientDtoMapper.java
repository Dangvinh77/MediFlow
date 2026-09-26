package com.mediflow.patient.application.mapper;

import com.mediflow.patient.application.dto.response.PatientDTO;
import com.mediflow.patient.domain.model.Patient;
import org.springframework.stereotype.Component;

/** Converts the English domain model to the locked Vietnamese public DTO. */
@Component
public final class PatientDtoMapper {

    public PatientDTO toDto(Patient patient) {
        return new PatientDTO(
                patient.patientId(),
                patient.fullName(),
                patient.dateOfBirth(),
                patient.gender(),
                patient.identityNumber(),
                patient.address(),
                patient.phoneNumber(),
                patient.email(),
                patient.healthInsuranceNumber(),
                patient.createdAt(),
                patient.updatedAt());
    }
}
