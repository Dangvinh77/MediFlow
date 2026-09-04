package com.mediflow.pharmacy.infrastructure.persistence.adapter;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import com.mediflow.pharmacy.application.port.out.StockReservationRepositoryPort;
import com.mediflow.pharmacy.domain.model.StockReservation;
import com.mediflow.pharmacy.domain.model.enums.ReservationStatus;
import com.mediflow.pharmacy.infrastructure.persistence.jpaEntity.StockReservationJpaEntity;
import com.mediflow.pharmacy.infrastructure.persistence.repository.StockReservationJpaRepository;

import lombok.RequiredArgsConstructor;

/**
 * Adapter cho {@link StockReservationRepositoryPort} — giữ chỗ tồn kho. Implement ngoài
 * {@code application}, đóng gói Spring Data JPA; mọi chuyện entity ↔ domain model nằm ở đây.
 */
@Component
@RequiredArgsConstructor
public class StockReservationPersistenceAdapter implements StockReservationRepositoryPort {

    private final StockReservationJpaRepository jpaRepo;

    @Override
    public StockReservation save(StockReservation reservation) {
        return toDomain(jpaRepo.save(toEntity(reservation)));
    }

    @Override
    public List<StockReservation> findByPrescription(UUID prescriptionId) {
        return jpaRepo.findByPrescriptionId(prescriptionId).stream().map(this::toDomain).toList();
    }

    /** {@inheritDoc} */
    @Override
    public List<StockReservation> findByPrescriptionForUpdate(UUID prescriptionId) {
        return jpaRepo.findByPrescriptionIdForUpdate(prescriptionId)
                .stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public List<StockReservation> findReservedByDrug(UUID drugId) {
        return jpaRepo.findByDrugIdAndStatus(drugId, ReservationStatus.RESERVED)
                .stream().map(this::toDomain).toList();
    }

    @Override
    public List<UUID> findExpiredPrescriptionIds(Instant now, int limit) {
        if (limit <= 0) {
            throw new IllegalArgumentException("Giới hạn batch phải lớn hơn 0");
        }
        return jpaRepo.findExpiredPrescriptionIds(
                ReservationStatus.RESERVED,
                now,
                PageRequest.of(0, limit));
    }

    @Override
    public Optional<StockReservation> findReservedByPrescriptionForUpdate(UUID prescriptionId, UUID drugId) {
        return jpaRepo.findByPrescriptionAndDrugForUpdate(prescriptionId, drugId).map(this::toDomain);
    }

    // ---- map entity ↔ domain (thủ công) ----

    private StockReservation toDomain(StockReservationJpaEntity e) {
        return StockReservation.restore(
                e.getReservationId(), e.getDrugId(), e.getPrescriptionId(), e.getQuantity(),
                e.getStatus(), e.getCreatedAt(), e.getExpiresAt(), e.getUpdatedAt(),
                e.getReleaseReason(), e.getReleasedAt(), e.getReleasedBy());
    }

    private StockReservationJpaEntity toEntity(StockReservation r) {
        return StockReservationJpaEntity.builder()
                .reservationId(r.getReservationId())
                .drugId(r.getDrugId())
                .prescriptionId(r.getPrescriptionId())
                .quantity(r.getQuantity())
                .status(r.getStatus())
                .expiresAt(r.getExpiresAt())
                .releaseReason(r.getReleaseReason())
                .releasedAt(r.getReleasedAt())
                .releasedBy(r.getReleasedBy())
                .build();
    }
}
