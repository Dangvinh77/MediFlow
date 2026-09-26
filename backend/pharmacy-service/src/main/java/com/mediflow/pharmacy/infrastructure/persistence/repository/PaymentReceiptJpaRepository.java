package com.mediflow.pharmacy.infrastructure.persistence.repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.mediflow.pharmacy.infrastructure.persistence.jpaentity.PaymentReceiptJpaEntity;

/** Spring Data repository cho bảng PAYMENT_RECEIPT. */
public interface PaymentReceiptJpaRepository extends JpaRepository<PaymentReceiptJpaEntity, UUID> {

    /** Tìm proof theo event id để phân biệt duplicate cùng payload và conflict. */
    Optional<PaymentReceiptJpaEntity> findByEventId(UUID eventId);

    /** Tìm mọi payment attempt của prescription mà không áp đặt one-to-one trước contract Billing. */
    List<PaymentReceiptJpaEntity> findByPrescriptionIdOrderByCreatedAtAsc(UUID prescriptionId);

    /**
     * Claim atomically theo unique event_id. Row mới luôn bắt đầu ở RECEIVED.
     * Business key ngoài event_id chưa được thêm cho đến khi Billing chốt contract.
     */
    @Modifying
    @Query(value = """
            INSERT INTO PAYMENT_RECEIPT (
                receipt_id, event_id, invoice_id, prescription_id, patient_id, department_id,
                total_amount, payment_method, payment_occurred_at, correlation_id,
                payload_fingerprint, status, failure_code, created_at, updated_at
            ) VALUES (
                :receiptId, :eventId, :invoiceId, :prescriptionId, :patientId, :departmentId,
                :totalAmount, :paymentMethod, :paymentOccurredAt, :correlationId,
                :payloadFingerprint, 'RECEIVED', NULL, now(), now()
            ) ON CONFLICT (event_id) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(
            @Param("receiptId") UUID receiptId,
            @Param("eventId") UUID eventId,
            @Param("invoiceId") UUID invoiceId,
            @Param("prescriptionId") UUID prescriptionId,
            @Param("patientId") UUID patientId,
            @Param("departmentId") UUID departmentId,
            @Param("totalAmount") BigDecimal totalAmount,
            @Param("paymentMethod") String paymentMethod,
            @Param("paymentOccurredAt") Instant paymentOccurredAt,
            @Param("correlationId") String correlationId,
            @Param("payloadFingerprint") String payloadFingerprint);

    /** Transitions a receipt once; a stale snapshot cannot overwrite a terminal outcome. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            UPDATE PAYMENT_RECEIPT
            SET status = :status, failure_code = :failureCode, updated_at = now()
            WHERE event_id = :eventId AND status = 'RECEIVED'
            """, nativeQuery = true)
    int finalizeIfReceived(
            @Param("eventId") UUID eventId,
            @Param("status") String status,
            @Param("failureCode") String failureCode);
}
