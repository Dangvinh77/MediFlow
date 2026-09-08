package com.mediflow.billing.infrastructure.persistence.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.mediflow.billing.infrastructure.persistence.jpaEntity.InvoiceJpaEntity;

/** Spring Data repository cho bảng {@code INVOICE} (backend-spec/06-billing.md §6, §12.3). */
public interface InvoiceJpaRepository extends JpaRepository<InvoiceJpaEntity, UUID> {

    /**
     * Hóa đơn của một đơn thuốc đã mở saga. Cột {@code prescription_id} có unique partial index
     * {@code uq_invoice_prescription} nên kết quả tối đa một (BR-B6).
     */
    Optional<InvoiceJpaEntity> findByPrescriptionId(UUID prescriptionId);

    /** Danh sách hóa đơn của một bệnh nhân, có phân trang. */
    Page<InvoiceJpaEntity> findByPatientId(UUID patientId, Pageable pageable);

    /**
     * Tổng hợp doanh thu đã thanh toán, gom theo khoa của từng khoản phí, trong khoảng
     * {@code [fromTs, toTs)} tính theo {@code paid_at} (BR-B10).
     *
     * <p>Nối {@code INVOICE} với {@code FEE} qua {@code fee.invoice_id} (không có quan hệ JPA giữa
     * hai aggregate — dùng theta join). {@code totalRevenue} cộng theo {@code fee.amount} để hóa đơn
     * có phí thuộc nhiều khoa không bị tính trùng; {@code invoiceCount} đếm hóa đơn phân biệt.
     * {@code departmentId} null → lấy mọi khoa.
     */
    @Query("""
            SELECT new com.mediflow.billing.infrastructure.persistence.repository.RevenueRow(
                       f.departmentId, SUM(f.amount), COUNT(DISTINCT i.invoiceId))
            FROM InvoiceJpaEntity i, FeeJpaEntity f
            WHERE f.invoiceId = i.invoiceId
              AND i.isPaid = true
              AND i.paidAt >= :fromTs
              AND i.paidAt < :toTs
              AND (:departmentId IS NULL OR f.departmentId = :departmentId)
            GROUP BY f.departmentId
            ORDER BY f.departmentId
            """)
    List<RevenueRow> sumRevenueByDepartment(@Param("departmentId") UUID departmentId,
                                            @Param("fromTs") Instant fromTs,
                                            @Param("toTs") Instant toTs);
}
