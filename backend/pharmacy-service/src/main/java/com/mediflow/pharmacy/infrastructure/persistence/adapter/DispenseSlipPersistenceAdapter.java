package com.mediflow.pharmacy.infrastructure.persistence.adapter;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.mediflow.pharmacy.application.port.out.DispenseSlipRepositoryPort;
import com.mediflow.pharmacy.domain.model.DispenseSlip;
import com.mediflow.pharmacy.infrastructure.persistence.jpaEntity.DispenseSlipJpaEntity;
import com.mediflow.pharmacy.infrastructure.persistence.repository.DispenseSlipJpaRepository;

import lombok.RequiredArgsConstructor;

/**
 * Adapter cho {@link DispenseSlipRepositoryPort} — phiếu xuất. Chú ý: phiếu ở trạng thái
 * FAILED vẫn phải save được (BR-D12) — {@code DispenseSlipJpaEntity} lưu {@code failureReason}
 * là cột nullable nên không gặp rào cản nào.
 */
@Component
@RequiredArgsConstructor
public class DispenseSlipPersistenceAdapter implements DispenseSlipRepositoryPort {

    private final DispenseSlipJpaRepository jpaRepo;

    @Override
    public DispenseSlip save(DispenseSlip slip) {
        DispenseSlipJpaEntity saved = jpaRepo.save(toEntity(slip));
        return toDomain(saved);
    }

    @Override
    public Optional<DispenseSlip> findById(UUID id) {
        return jpaRepo.findById(id).map(this::toDomain);
    }

    @Override
    public Optional<DispenseSlip> findByPrescription(UUID prescriptionId) {
        return jpaRepo.findByPrescriptionId(prescriptionId).map(this::toDomain);
    }

    /** {@inheritDoc} */
    @Override
    public Optional<DispenseSlip> findByPrescriptionForUpdate(UUID prescriptionId) {
        return jpaRepo.findByPrescriptionIdForUpdate(prescriptionId).map(this::toDomain);
    }

    // ---- map entity ↔ domain (thủ công) ----

    private DispenseSlip toDomain(DispenseSlipJpaEntity e) {
        return DispenseSlip.restore(
                e.getDispenseId(), e.getPrescriptionId(), e.getStatus(),
                e.getDispensedAt(), e.getDispensedBy(), e.getFailureReason(),
                e.getCreatedAt(), e.getUpdatedAt());
    }

    private DispenseSlipJpaEntity toEntity(DispenseSlip s) {
        return DispenseSlipJpaEntity.builder()
                .dispenseId(s.getDispenseId())
                .prescriptionId(s.getPrescriptionId())
                .status(s.getStatus())
                .dispensedAt(s.getDispensedAt())
                .dispensedBy(s.getDispensedBy())
                .failureReason(s.getFailureReason())
                .build();
    }
}
