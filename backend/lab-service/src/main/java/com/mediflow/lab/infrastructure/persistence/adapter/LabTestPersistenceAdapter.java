package com.mediflow.lab.infrastructure.persistence.adapter;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import com.mediflow.common.api.PageQuery;
import com.mediflow.common.api.PageResult;
import com.mediflow.lab.application.port.out.LabTestRepositoryPort;
import com.mediflow.lab.domain.model.LabResult;
import com.mediflow.lab.domain.model.LabTest;
import com.mediflow.lab.domain.model.LabTestStatus;
import com.mediflow.lab.infrastructure.persistence.jpaEntity.LabResultJpaEntity;
import com.mediflow.lab.infrastructure.persistence.jpaEntity.LabTestJpaEntity;
import com.mediflow.lab.infrastructure.persistence.repository.LabTestJpaRepository;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class LabTestPersistenceAdapter implements LabTestRepositoryPort {
    private final LabTestJpaRepository repository;

    @Override public LabTest save(LabTest value) { return toDomain(repository.saveAndFlush(toEntity(value))); }
    @Override public Optional<LabTest> findById(UUID id) { return repository.findAggregateById(id).map(this::toDomain); }
    @Override public Optional<LabTest> findByIdForUpdate(UUID id) { return repository.findByIdForUpdate(id).map(this::toDomain); }
    @Override public List<LabTest> findByPatient(UUID patientId) {
        return repository.findByPatientIdOrderByRequestedDateDesc(patientId).stream().map(this::toDomain).toList();
    }
    @Override public List<LabTest> findByRecord(UUID recordId) {
        return repository.findByRecordIdOrderByRequestedDateDesc(recordId).stream().map(this::toDomain).toList();
    }
    @Override public PageResult<LabTest> search(UUID departmentId, LabTestStatus status, PageQuery query) {
        PageRequest pageable = PageRequest.of(query.page(), query.size(),
                Sort.by(Sort.Direction.DESC, "requestedDate", "createdAt"));
        Page<LabTestJpaEntity> page;
        if (departmentId != null && status != null) {
            page = repository.findByRequestingDepartmentIdAndStatus(departmentId, status, pageable);
        } else if (departmentId != null) {
            page = repository.findByRequestingDepartmentId(departmentId, pageable);
        } else if (status != null) {
            page = repository.findByStatus(status, pageable);
        } else {
            page = repository.findAll(pageable);
        }
        return PageResult.of(page.getContent().stream().map(this::toDomain).toList(),
                page.getTotalElements(), query.page(), query.size());
    }

    private LabTest toDomain(LabTestJpaEntity e) {
        List<LabResult> results = e.getResults().stream().map(r -> LabResult.restore(r.getResultId(),
                r.getIndicator(), r.getValue(), r.getUnit(), r.getReferenceRange())).toList();
        return LabTest.restore(e.getTestId(), e.getRecordId(), e.getPatientId(), e.getRequestingDepartmentId(),
                e.getTestType(), e.getRequestedDate(), e.getPerformedDate(), e.getStatus(), e.getConclusion(),
                e.isPaid(), results, e.getCreatedAt(), e.getUpdatedAt());
    }
    private LabTestJpaEntity toEntity(LabTest t) {
        LabTestJpaEntity entity = LabTestJpaEntity.builder().testId(t.getTestId()).recordId(t.getRecordId())
                .patientId(t.getPatientId()).requestingDepartmentId(t.getRequestingDepartmentId())
                .testType(t.getLabType()).requestedDate(t.getRequestedDate()).performedDate(t.getPerformedDate())
                .status(t.getStatus()).conclusion(t.getConclusion()).paid(t.isPaid())
                .createdAt(t.getCreatedAt()).updatedAt(t.getUpdatedAt()).build();
        List<LabResultJpaEntity> results = t.getResults().stream().map(r -> LabResultJpaEntity.builder()
                .resultId(r.getResultId()).test(entity).indicator(r.getIndicator()).value(r.getValue())
                .unit(r.getUnit()).referenceRange(r.getReferenceRange()).build()).toList();
        entity.setResults(new ArrayList<>(results));
        return entity;
    }
}
