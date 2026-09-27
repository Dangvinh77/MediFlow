package com.mediflow.inpatient.infrastructure.persistence.adapter;

import com.mediflow.common.api.PageResult;
import com.mediflow.inpatient.application.dto.query.AdmissionSearchQuery;
import com.mediflow.inpatient.application.port.out.AdmissionRepositoryPort;
import com.mediflow.inpatient.domain.model.Admission;
import com.mediflow.inpatient.infrastructure.persistence.jpaEntity.AdmissionJpaEntity;
import com.mediflow.inpatient.infrastructure.persistence.mapper.InpatientPersistenceMapper;
import com.mediflow.inpatient.infrastructure.persistence.repository.AdmissionJpaRepository;
import com.mediflow.inpatient.infrastructure.persistence.repository.FinancialClearanceJpaRepository;
import java.sql.ResultSet;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(name = "mediflow.inpatient.persistence.enabled", havingValue = "true", matchIfMissing = true)
public class AdmissionPersistenceAdapter implements AdmissionRepositoryPort {
    private final AdmissionJpaRepository repository;
    private final FinancialClearanceJpaRepository clearances;
    private final InpatientPersistenceMapper mapper;
    private final JdbcTemplate jdbcTemplate;

    public AdmissionPersistenceAdapter(AdmissionJpaRepository repository,
                                       FinancialClearanceJpaRepository clearances,
                                       InpatientPersistenceMapper mapper,
                                       JdbcTemplate jdbcTemplate) {
        this.repository = repository;
        this.clearances = clearances;
        this.mapper = mapper;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<Admission> findById(UUID admissionId) {
        return repository.findById(admissionId).map(this::toDomain);
    }

    @Override
    public Optional<Admission> findByIdForUpdate(UUID admissionId) {
        return repository.lockById(admissionId).map(this::toDomain);
    }

    @Override
    public Optional<Admission> findByAdmissionRequestId(UUID requestId) {
        jdbcTemplate.query("SELECT pg_advisory_xact_lock(hashtextextended(CAST(? AS text), 0))",
                (ResultSet resultSet) -> null, requestId.toString());
        return repository.findByMaYeuCauNoiTru(requestId).map(this::toDomain);
    }

    @Override
    public Admission save(Admission admission) {
        AdmissionJpaEntity row = repository.findById(admission.admissionId())
                .orElseGet(AdmissionJpaEntity::new);
        return toDomain(repository.save(mapper.copy(admission, row)));
    }

    @Override
    public Admission saveAndFlush(Admission admission) {
        AdmissionJpaEntity row = repository.findById(admission.admissionId())
                .orElseGet(AdmissionJpaEntity::new);
        return toDomain(repository.saveAndFlush(mapper.copy(admission, row)));
    }

    @Override
    public PageResult<Admission> search(AdmissionSearchQuery query) {
        Instant from = query.tuNgay() == null ? null : query.tuNgay().atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant to = query.denNgay() == null ? null : query.denNgay().plusDays(1)
                .atStartOfDay().toInstant(ZoneOffset.UTC);
        var page = repository.search(query.maKhoa(), query.maBenhNhan(), query.status(), from, to,
                PageRequest.of(query.phanTrang().page(), query.phanTrang().size()));
        return PageResult.of(page.getContent().stream().map(this::toDomain).toList(),
                page.getTotalElements(), page.getNumber(), page.getSize());
    }

    private Admission toDomain(AdmissionJpaEntity row) {
        Instant depositExpiresAt = row.maXacNhanTamUng == null
                ? null : clearances.findExpiryById(row.maXacNhanTamUng).orElse(null);
        return mapper.toDomain(row, depositExpiresAt);
    }
}
