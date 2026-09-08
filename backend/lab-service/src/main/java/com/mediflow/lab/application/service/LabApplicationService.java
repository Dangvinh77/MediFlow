package com.mediflow.lab.application.service;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mediflow.common.api.PageQuery;
import com.mediflow.common.api.PageResult;
import com.mediflow.lab.application.dto.request.AddResultRequest;
import com.mediflow.lab.application.dto.request.CreateLabRequest;
import com.mediflow.lab.application.dto.response.LabTestDTO;
import com.mediflow.lab.application.event.LabRequestCreatedEvent;
import com.mediflow.lab.application.event.LabResultCreatedEvent;
import com.mediflow.lab.application.mapper.LabTestDtoMapper;
import com.mediflow.lab.application.port.in.ManageLabTestUseCase;
import com.mediflow.lab.application.port.in.ReactToClinicalUseCase;
import com.mediflow.lab.application.port.out.LabEventPublisherPort;
import com.mediflow.lab.application.port.out.LabTestRepositoryPort;
import com.mediflow.lab.domain.exception.LabTestNotFoundException;
import com.mediflow.lab.domain.model.LabResult;
import com.mediflow.lab.domain.model.LabTest;
import com.mediflow.lab.domain.model.LabTestStatus;

@Service
public class LabApplicationService implements ManageLabTestUseCase, ReactToClinicalUseCase {

    private final LabTestRepositoryPort tests;
    private final LabEventPublisherPort publisher;
    private final LabTestDtoMapper mapper;

    public LabApplicationService(LabTestRepositoryPort tests, LabEventPublisherPort publisher,
                                 LabTestDtoMapper mapper) {
        this.tests = tests;
        this.publisher = publisher;
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public LabTestDTO create(CreateLabRequest request) {
        LabTest saved = saveNew(request.recordId(), request.patientId(), request.requestingDepartmentId(),
                request.labType(), request.requestedDate());
        return mapper.toDto(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public LabTestDTO getById(UUID id) {
        return mapper.toDto(tests.findById(id).orElseThrow(() -> new LabTestNotFoundException(id)));
    }

    @Override
    @Transactional(readOnly = true)
    public List<LabTestDTO> byPatient(UUID patientId) {
        return tests.findByPatient(patientId).stream().map(mapper::toDto).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<LabTestDTO> search(UUID departmentId, LabTestStatus status, PageQuery page) {
        return tests.search(departmentId, status, page).map(mapper::toDto);
    }

    @Override
    @Transactional
    public LabTestDTO addResults(UUID id, AddResultRequest request) {
        LabTest test = locked(id);
        List<LabResult> results = request.results().stream()
                .map(item -> LabResult.create(item.indicator(), item.value(), item.unit(), item.referenceRange()))
                .toList();
        test.recordResults(results, request.conclusion(), request.performedDate());
        LabTest saved = tests.save(test);
        publisher.publishResultCreated(LabResultCreatedEvent.from(saved, correlationId()));
        return mapper.toDto(saved);
    }

    @Override
    @Transactional
    public LabTestDTO changeStatus(UUID id, LabTestStatus status) {
        LabTest test = locked(id);
        test.changeStatus(status);
        return mapper.toDto(tests.save(test));
    }

    @Override
    @Transactional
    public void autoCreateFromRecord(UUID recordId, UUID patientId, UUID departmentId, String labType) {
        if (labType == null || labType.isBlank()) {
            return;
        }
        saveNew(recordId, patientId, departmentId, labType, LocalDate.now());
    }

    @Override
    @Transactional
    public void markPaid(UUID testId) {
        LabTest test = locked(testId);
        test.markPaid();
        tests.save(test);
    }

    private LabTest saveNew(UUID recordId, UUID patientId, UUID departmentId,
                            String labType, LocalDate requestedDate) {
        LabTest saved = tests.save(LabTest.create(recordId, patientId, departmentId, labType, requestedDate));
        publisher.publishRequestCreated(LabRequestCreatedEvent.from(saved, correlationId()));
        return saved;
    }

    private LabTest locked(UUID id) {
        return tests.findByIdForUpdate(id).orElseThrow(() -> new LabTestNotFoundException(id));
    }

    private String correlationId() {
        return UUID.randomUUID().toString();
    }
}
