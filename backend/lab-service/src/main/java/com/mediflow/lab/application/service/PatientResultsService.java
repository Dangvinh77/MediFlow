package com.mediflow.lab.application.service;
import com.mediflow.lab.application.port.out.*;
import com.mediflow.lab.application.mapper.LabTestDtoMapper;
import com.mediflow.lab.application.dto.response.PatientLabResultDTO;
import com.mediflow.common.api.*;
import com.mediflow.common.exception.ForbiddenOperationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class PatientResultsService {
    private final LabTestRepositoryPort tests;
    private final CurrentPatientIdentityPort actors;
    private final LabTestDtoMapper mapper;
    public PatientResultsService(LabTestRepositoryPort tests, CurrentPatientIdentityPort actors, LabTestDtoMapper mapper) {
        this.tests = tests; this.actors = actors; this.mapper = mapper;
    }
    public PageResult<PatientLabResultDTO> mine(PageQuery page) {
        var patientId = actors.patientId();
        if (patientId == null) throw new ForbiddenOperationException("PATIENT_IDENTITY_REQUIRED", "A verified patient account is required");
        return tests.findCompletedByPatient(patientId, page).map(mapper::toDto).map(PatientLabResultDTO::from);
    }
}
