package com.mediflow.inpatient.infrastructure.persistence.adapter;

import com.mediflow.inpatient.application.port.out.SurgeryEventReceiptRepositoryPort;
import com.mediflow.inpatient.domain.model.SurgeryEventReceipt;
import com.mediflow.inpatient.infrastructure.persistence.jpaEntity.SurgeryEventReceiptJpaEntity;
import com.mediflow.inpatient.infrastructure.persistence.mapper.InpatientPersistenceMapper;
import com.mediflow.inpatient.infrastructure.persistence.repository.SurgeryEventReceiptJpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(name = "mediflow.inpatient.persistence.enabled", havingValue = "true", matchIfMissing = true)
public class SurgeryEventReceiptPersistenceAdapter implements SurgeryEventReceiptRepositoryPort {
    private final SurgeryEventReceiptJpaRepository receipts;
    private final InpatientPersistenceMapper mapper;

    public SurgeryEventReceiptPersistenceAdapter(SurgeryEventReceiptJpaRepository receipts,
                                                 InpatientPersistenceMapper mapper) {
        this.receipts = receipts;
        this.mapper = mapper;
    }

    @Override
    public Optional<SurgeryEventReceipt> findByEventId(UUID eventId) {
        return receipts.findByMaSuKien(eventId).map(mapper::toDomain);
    }

    @Override
    public Optional<SurgeryEventReceipt> findByBusinessOperation(String eventType, UUID operationId) {
        return receipts.findByLoaiSuKienAndMaNghiepVu(eventType, operationId).map(mapper::toDomain);
    }

    @Override
    public List<SurgeryEventReceipt> findByCaseId(UUID surgeryCaseId) {
        return receipts.findByMaCaMoOrderByNhanLucAsc(surgeryCaseId).stream()
                .map(mapper::toDomain).toList();
    }

    @Override
    public SurgeryEventReceipt save(SurgeryEventReceipt receipt) {
        SurgeryEventReceiptJpaEntity row = receipts.findById(receipt.receiptId())
                .orElseGet(SurgeryEventReceiptJpaEntity::new);
        return mapper.toDomain(receipts.save(mapper.copy(receipt, row)));
    }
}
