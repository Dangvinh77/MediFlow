package com.mediflow.pharmacy.infrastructure.persistence.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.mediflow.pharmacy.domain.model.enums.ReservationStatus;
import com.mediflow.pharmacy.infrastructure.persistence.jpaEntity.StockReservationJpaEntity;

import jakarta.persistence.LockModeType;

/** Spring Data repository cho các dòng giữ chỗ tồn kho. */
public interface StockReservationJpaRepository extends JpaRepository<StockReservationJpaEntity, UUID> {

    /** Giữ chỗ của một đơn — dispense cần biết đơn đã giữ những gì. */
    List<StockReservationJpaEntity> findByPrescriptionId(UUID prescriptionId);

    /** Các giữ chỗ đang RESERVED của một thuốc — tính tồn khả dụng khi kê đơn. */
    List<StockReservationJpaEntity> findByDrugIdAndStatus(UUID drugId, ReservationStatus status);

    /**
     * Khóa toàn bộ reservation của một đơn theo thứ tự thuốc ổn định.
     *
     * @param prescriptionId mã đơn thuốc
     * @return các entity đã khóa
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT r FROM StockReservationJpaEntity r
            WHERE r.prescriptionId = :prescriptionId
            ORDER BY r.drugId
            """)
    List<StockReservationJpaEntity> findByPrescriptionIdForUpdate(
            @Param("prescriptionId") UUID prescriptionId);

    /** Khóa ghi giữ chỗ của (đơn, thuốc) — dành riêng luồng dispense (chống tương tranh với job release, BR-D10). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM StockReservationJpaEntity r WHERE r.prescriptionId = :prescriptionId AND r.drugId = :drugId")
    Optional<StockReservationJpaEntity> findByPrescriptionAndDrugForUpdate(
            @Param("prescriptionId") UUID prescriptionId, @Param("drugId") UUID drugId);

    /**
     * Tìm các prescription id có reservation {@code RESERVED} đã hết TTL.
     *
     * @param status trạng thái đang giữ
     * @param now mốc thời gian đánh giá
     * @param pageable giới hạn kích thước batch
     * @return danh sách mã đơn không trùng nhau
     */
    @Query("""
            SELECT DISTINCT r.prescriptionId FROM StockReservationJpaEntity r
            WHERE r.status = :status AND r.expiresAt <= :now
            ORDER BY r.prescriptionId
            """)
    List<UUID> findExpiredPrescriptionIds(
            @Param("status") ReservationStatus status,
            @Param("now") java.time.Instant now,
            Pageable pageable);
}
