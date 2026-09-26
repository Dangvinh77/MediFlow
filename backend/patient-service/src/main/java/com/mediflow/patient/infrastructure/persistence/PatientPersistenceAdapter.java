package com.mediflow.patient.infrastructure.persistence;

import com.mediflow.common.api.PageQuery;
import com.mediflow.common.api.PageResult;
import com.mediflow.patient.application.port.out.PatientRepositoryPort;
import com.mediflow.patient.domain.model.Patient;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public class PatientPersistenceAdapter implements PatientRepositoryPort {

    private final PatientJpaRepository repository;
    private final PatientPersistenceMapper mapper;

    public PatientPersistenceAdapter(PatientJpaRepository repository, PatientPersistenceMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    public Optional<Patient> findById(UUID patientId) {
        return repository.findById(patientId).map(mapper::toDomain);
    }

    @Override
    public PageResult<Patient> search(String keyword, PageQuery query) {
        var page = repository.search(keyword == null || keyword.isBlank() ? null : keyword.trim(),
                PageRequest.of(query.page(), query.size()));
        return PageResult.of(page.getContent().stream().map(mapper::toDomain).toList(),
                page.getTotalElements(), page.getNumber(), page.getSize());
    }

    @Override
    public boolean existsById(UUID patientId) {
        return repository.existsById(patientId);
    }
}
