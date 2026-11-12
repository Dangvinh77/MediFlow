package com.mediflow.patient.infrastructure.persistence;

import com.mediflow.common.api.PageQuery;
import com.mediflow.common.api.PageResult;
import com.mediflow.patient.application.port.out.PatientRepositoryPort;
import com.mediflow.patient.domain.model.Patient;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
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
    public Patient save(Patient patient) {
        PatientJpaEntity entity = mapper.toEntity(patient);
        repository.findById(patient.patientId()).ifPresent(existing -> {
            if (sameEmail(existing.getEmail(), patient.email())) {
                entity.withEmailUniqueKey(existing.getEmailUniqueKey());
            }
        });
        return mapper.toDomain(repository.save(entity));
    }

    @Override
    public Optional<Patient> findByIdForUpdate(UUID patientId) {
        return repository.findByIdForUpdate(patientId).map(mapper::toDomain);
    }

    @Override
    public PageResult<Patient> search(String keyword, PageQuery query) {
        var page = repository.search(keyword == null || keyword.isBlank() ? "" : keyword.trim(),
                PageRequest.of(query.page(), query.size(),
                        Sort.by(Sort.Order.desc("createdAt"), Sort.Order.asc("patientId"))));
        return PageResult.of(page.getContent().stream().map(mapper::toDomain).toList(),
                page.getTotalElements(), page.getNumber(), page.getSize());
    }

    @Override
    public boolean existsById(UUID patientId) {
        return repository.existsById(patientId);
    }

    @Override
    public boolean existsByIdentityNumber(String identityNumber) {
        return repository.existsByIdentityNumber(identityNumber);
    }

    @Override
    public boolean existsByIdentityNumberAndPatientIdNot(String identityNumber, UUID patientId) {
        return repository.existsByIdentityNumberAndPatientIdNot(identityNumber, patientId);
    }

    @Override
    public boolean existsByEmail(String normalizedEmail) {
        return repository.existsByNormalizedEmail(normalizeEmail(normalizedEmail));
    }

    @Override
    public boolean existsByEmailAndPatientIdNot(String normalizedEmail, UUID patientId) {
        return repository.existsByNormalizedEmailAndPatientIdNot(normalizeEmail(normalizedEmail), patientId);
    }

    private static String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(java.util.Locale.ROOT);
    }

    private static boolean sameEmail(String first, String second) {
        return java.util.Objects.equals(normalizeEmail(first), normalizeEmail(second));
    }

    @Override
    public void deleteById(UUID patientId) {
        repository.deleteById(patientId);
    }
}
