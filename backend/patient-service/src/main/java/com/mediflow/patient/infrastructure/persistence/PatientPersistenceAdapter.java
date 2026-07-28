package com.mediflow.patient.infrastructure.persistence;

import com.mediflow.common.api.PageQuery;
import com.mediflow.common.api.PageResult;
import com.mediflow.patient.application.port.out.PatientRepositoryPort;
import com.mediflow.patient.domain.model.Patient;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * Implements {@link PatientRepositoryPort} with JPA.
 *
 * <p>This class is the only place where Spring Data types meet domain types. It converts
 * {@code PageQuery → Pageable} on the way in and {@code Page → PageResult} on the way out, so
 * nothing above it ever imports {@code org.springframework.data}.
 */
@Component
public class PatientPersistenceAdapter implements PatientRepositoryPort {

    private final PatientJpaRepository jpa;
    private final PatientPersistenceMapper mapper;

    public PatientPersistenceAdapter(PatientJpaRepository jpa, PatientPersistenceMapper mapper) {
        this.jpa = jpa;
        this.mapper = mapper;
    }

    @Override
    public Patient save(Patient patient) {
        return mapper.toDomain(jpa.save(mapper.toEntity(patient)));
    }

    @Override
    public Optional<Patient> findById(UUID maBenhNhan) {
        return jpa.findById(maBenhNhan).map(mapper::toDomain);
    }

    @Override
    public PageResult<Patient> search(String keyword, PageQuery pageQuery) {
        Pageable pageable = PageRequest.of(pageQuery.page(), pageQuery.size(), Sort.by("hoTen"));
        Page<PatientJpaEntity> page = jpa.search(keyword, pageable);

        return PageResult.of(
                page.getContent().stream().map(mapper::toDomain).toList(),
                page.getTotalElements(),
                page.getNumber(),
                page.getSize());
    }

    @Override
    public boolean existsBySoCmnd(String soCmnd) {
        return jpa.existsBySoCmnd(soCmnd);
    }

    @Override
    public void deleteById(UUID maBenhNhan) {
        jpa.deleteById(maBenhNhan);
    }
}
