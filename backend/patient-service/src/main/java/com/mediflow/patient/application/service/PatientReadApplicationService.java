package com.mediflow.patient.application.service;

import com.mediflow.common.api.PageQuery;
import com.mediflow.common.api.PageResult;
import com.mediflow.patient.application.dto.response.PatientDTO;
import com.mediflow.patient.application.dto.response.PatientLookupDTO;
import com.mediflow.patient.application.mapper.PatientDtoMapper;
import com.mediflow.patient.application.port.in.GetPatientUseCase;
import com.mediflow.patient.application.port.in.LookupPatientUseCase;
import com.mediflow.patient.application.port.out.PatientRepositoryPort;
import com.mediflow.patient.domain.exception.PatientNotFoundException;
import com.mediflow.patient.domain.model.Patient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class PatientReadApplicationService implements GetPatientUseCase, LookupPatientUseCase {

    private final PatientRepositoryPort patients;
    private final PatientDtoMapper mapper;

    public PatientReadApplicationService(
            PatientRepositoryPort patients,
            PatientDtoMapper mapper) {
        this.patients = patients;
        this.mapper = mapper;
    }

    @Override
    public PatientDTO getById(UUID patientId) {
        Patient patient = patients.findById(patientId)
                .orElseThrow(() -> new PatientNotFoundException(patientId));
        return mapper.toDto(patient);
    }

    @Override
    public PageResult<PatientDTO> search(String keyword, PageQuery page) {
        String normalizedKeyword = keyword == null || keyword.isBlank()
                ? null
                : keyword.trim();
        return patients.search(normalizedKeyword, page).map(mapper::toDto);
    }

    @Override
    public PatientLookupDTO exists(UUID patientId) {
        return new PatientLookupDTO(patients.existsById(patientId), patientId);
    }
}
