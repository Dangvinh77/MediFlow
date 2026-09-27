package com.mediflow.inpatient.infrastructure.persistence.adapter;

import com.mediflow.inpatient.application.port.out.InpatientEventStorePort;
import com.mediflow.inpatient.domain.model.AdmissionStatusHistory;
import com.mediflow.inpatient.domain.model.DepositTopupRequest;
import com.mediflow.inpatient.domain.model.FinancialClearance;
import com.mediflow.inpatient.domain.model.SettlementSnapshot;
import com.mediflow.inpatient.domain.model.enums.OverrideType;
import com.mediflow.inpatient.infrastructure.persistence.jpaEntity.AdmissionStatusHistoryJpaEntity;
import com.mediflow.inpatient.infrastructure.persistence.jpaEntity.DepositTopupRequestJpaEntity;
import com.mediflow.inpatient.infrastructure.persistence.jpaEntity.FinancialClearanceJpaEntity;
import com.mediflow.inpatient.infrastructure.persistence.jpaEntity.OverrideJpaEntity;
import com.mediflow.inpatient.infrastructure.persistence.jpaEntity.SettlementSnapshotJpaEntity;
import com.mediflow.inpatient.infrastructure.persistence.mapper.InpatientPersistenceMapper;
import com.mediflow.inpatient.infrastructure.persistence.repository.AdmissionStatusHistoryJpaRepository;
import com.mediflow.inpatient.infrastructure.persistence.repository.DepositTopupRequestJpaRepository;
import com.mediflow.inpatient.infrastructure.persistence.repository.FinancialClearanceJpaRepository;
import com.mediflow.inpatient.infrastructure.persistence.repository.OverrideJpaRepository;
import com.mediflow.inpatient.infrastructure.persistence.repository.SettlementSnapshotJpaRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional
@ConditionalOnProperty(name = "mediflow.inpatient.persistence.enabled", havingValue = "true", matchIfMissing = true)
public class InpatientEventStorePersistenceAdapter implements InpatientEventStorePort {
    private final AdmissionStatusHistoryJpaRepository histories;
    private final FinancialClearanceJpaRepository clearances;
    private final SettlementSnapshotJpaRepository settlements;
    private final DepositTopupRequestJpaRepository topups;
    private final OverrideJpaRepository overrides;
    private final InpatientPersistenceMapper mapper;

    public InpatientEventStorePersistenceAdapter(AdmissionStatusHistoryJpaRepository histories,
                                                 FinancialClearanceJpaRepository clearances,
                                                 SettlementSnapshotJpaRepository settlements,
                                                 DepositTopupRequestJpaRepository topups,
                                                 OverrideJpaRepository overrides,
                                                 InpatientPersistenceMapper mapper) {
        this.histories = histories;
        this.clearances = clearances;
        this.settlements = settlements;
        this.topups = topups;
        this.overrides = overrides;
        this.mapper = mapper;
    }

    @Override
    public void appendHistory(AdmissionStatusHistory history) {
        histories.save(mapper.copy(history, new AdmissionStatusHistoryJpaEntity()));
    }

    @Override
    public void saveClearance(FinancialClearance clearance) {
        FinancialClearanceJpaEntity row = clearances.findById(clearance.maXacNhan())
                .orElseGet(FinancialClearanceJpaEntity::new);
        clearances.save(mapper.copy(clearance, row));
    }

    @Override
    public void saveSettlement(SettlementSnapshot settlement) {
        SettlementSnapshotJpaEntity row = settlements.findById(settlement.maQuyetToan())
                .orElseGet(SettlementSnapshotJpaEntity::new);
        settlements.save(mapper.copy(settlement, row));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<SettlementSnapshot> findLatestSettlement(UUID admissionId) {
        List<SettlementSnapshotJpaEntity> rows = settlements
                .findByMaDotNoiTruOrderByHoanTatLucDescMaQuyetToanDesc(admissionId);
        return rows.stream().findFirst().map(mapper::toDomain);
    }

    @Override
    public void saveTopupRequest(DepositTopupRequest request) {
        DepositTopupRequestJpaEntity row = topups.findById(request.maYeuCauBoSung())
                .orElseGet(DepositTopupRequestJpaEntity::new);
        topups.save(mapper.copy(request, row));
    }

    @Override
    public void saveOverride(UUID overrideId, UUID admissionId, OverrideType type, UUID approvedBy,
                             String approverRole, String reason, Instant approvedAt) {
        OverrideJpaEntity row = overrides.findById(overrideId)
                .orElseGet(() -> mapper.override(overrideId, admissionId, type, approvedBy,
                        approverRole, reason, approvedAt));
        if (!overrideId.equals(row.maPheDuyet)) {
            throw new IllegalStateException("Override identifier changed while persisting audit");
        }
        overrides.save(row);
    }
}
