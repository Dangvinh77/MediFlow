package com.mediflow.inpatient.infrastructure.persistence.adapter;

import com.mediflow.inpatient.application.port.out.DischargeSummaryRepositoryPort;
import com.mediflow.inpatient.domain.model.DischargeSummary;
import com.mediflow.inpatient.infrastructure.persistence.jpaEntity.DischargeSummaryJpaEntity;
import com.mediflow.inpatient.infrastructure.persistence.mapper.InpatientPersistenceMapper;
import com.mediflow.inpatient.infrastructure.persistence.repository.DischargeSummaryJpaRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(name = "mediflow.inpatient.persistence.enabled", havingValue = "true", matchIfMissing = true)
public class DischargeSummaryPersistenceAdapter implements DischargeSummaryRepositoryPort {
    private final DischargeSummaryJpaRepository summaries;
    private final InpatientPersistenceMapper mapper;

    public DischargeSummaryPersistenceAdapter(DischargeSummaryJpaRepository summaries,
                                              InpatientPersistenceMapper mapper) {
        this.summaries = summaries;
        this.mapper = mapper;
    }

    @Override
    public Optional<DischargeSummary> findByAdmissionId(UUID admissionId) {
        return summaries.findByMaDotNoiTru(admissionId).map(row -> new DischargeSummary(
                row.maTomTat, row.maDotNoiTru, row.tomTatChanDoan, row.tomTatDieuTri,
                row.ketQua, row.keHoachTheoDoi, row.nguoiDuyet, row.thoiGianDuyet));
    }

    @Override
    public DischargeSummary save(DischargeSummary summary) {
        DischargeSummaryJpaEntity row = summaries.findById(summary.maTomTat())
                .orElseGet(DischargeSummaryJpaEntity::new);
        summaries.save(mapper.copy(summary, row));
        return summary;
    }
}
