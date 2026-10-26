package com.mediflow.clinical.infrastructure.persistence.adapter;

import com.mediflow.clinical.application.port.out.ClinicalLabOrderRepositoryPort;
import com.mediflow.clinical.domain.model.ClinicalLabOrder;
import com.mediflow.clinical.infrastructure.persistence.jpaEntity.ClinicalLabOrderJpaEntity;
import com.mediflow.clinical.infrastructure.persistence.repository.ClinicalLabOrderJpaRepository;
import java.util.Optional;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class ClinicalLabOrderPersistenceAdapter implements ClinicalLabOrderRepositoryPort {
    private final ClinicalLabOrderJpaRepository repository;

    @Override
    public ClinicalLabOrder save(ClinicalLabOrder order) {
        return toDomain(repository.saveAndFlush(toEntity(order)));
    }

    @Override
    public Optional<ClinicalLabOrder> findById(UUID sourceOrderId) {
        return repository.findById(sourceOrderId).map(ClinicalLabOrderPersistenceAdapter::toDomain);
    }

    @Override
    public List<ClinicalLabOrder> findByRecordId(UUID recordId) {
        return repository.findByRecordIdOrderByOrderedAtAscSourceOrderIdAsc(recordId).stream()
                .map(ClinicalLabOrderPersistenceAdapter::toDomain)
                .toList();
    }

    private static ClinicalLabOrderJpaEntity toEntity(ClinicalLabOrder order) {
        return ClinicalLabOrderJpaEntity.builder()
                .sourceOrderId(order.sourceOrderId())
                .recordId(order.recordId())
                .patientId(order.patientId())
                .requestingDoctorId(order.requestingDoctorId())
                .requestingDepartmentId(order.requestingDepartmentId())
                .careEpisodeType(order.careEpisodeType())
                .careEpisodeId(order.careEpisodeId())
                .labType(order.labType())
                .requestedDate(order.requestedDate())
                .priceCode(order.priceCode())
                .orderedAt(order.orderedAt())
                .build();
    }

    private static ClinicalLabOrder toDomain(ClinicalLabOrderJpaEntity entity) {
        return new ClinicalLabOrder(entity.getSourceOrderId(), entity.getRecordId(), entity.getPatientId(),
                entity.getRequestingDoctorId(), entity.getRequestingDepartmentId(), entity.getCareEpisodeType(),
                entity.getCareEpisodeId(), entity.getLabType(), entity.getRequestedDate(), entity.getPriceCode(),
                entity.getOrderedAt());
    }
}
