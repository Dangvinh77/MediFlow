package com.mediflow.billing.infrastructure.persistence.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.mediflow.billing.domain.model.FeeType;
import com.mediflow.billing.infrastructure.persistence.jpaEntity.FeeJpaEntity;

/**
 * Spring Data repository cho bảng {@code FEE}. Chỉ khai các truy vấn tầng application cần
 * (backend-spec/06-billing.md §6, §12.3); còn lại dùng {@code JpaRepository} sẵn có.
 */
public interface FeeJpaRepository extends JpaRepository<FeeJpaEntity, UUID> {

    /**
     * Tuyến phòng thủ BR-B7 — đã có khoản phí sinh từ đúng event nguồn này chưa. Unique partial
     * index {@code uq_fee_source} là lớp chặn cuối khi hai message đến tương tranh.
     */
    boolean existsByFeeTypeAndSourceRefId(FeeType feeType, UUID sourceRefId);

    /** Các khoản phí chưa thanh toán của bệnh nhân — dùng để cộng {@code totalAmount} khi lập hóa đơn (BR-B2). */
    @Query("SELECT f FROM FeeJpaEntity f WHERE f.patientId = :patientId AND f.isPaid = false")
    List<FeeJpaEntity> findUnpaidByPatient(@Param("patientId") UUID patientId);

    /** Các khoản phí đã gắn vào một hóa đơn — dùng khi thanh toán để {@code markPaid()} từng khoản (BR-B3). */
    List<FeeJpaEntity> findByInvoiceId(UUID invoiceId);
}
