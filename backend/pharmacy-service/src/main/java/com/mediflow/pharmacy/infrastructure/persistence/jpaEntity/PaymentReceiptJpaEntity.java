package com.mediflow.pharmacy.infrastructure.persistence.jpaEntity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import com.mediflow.pharmacy.domain.model.enums.PaymentReceiptStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * JPA entity cho PAYMENT_RECEIPT.
 *
 * <p>Chỉ lưu UUID tham chiếu đến bounded context khác; tuyệt đối không tạo JPA relation sang
 * Billing, Patient hoặc Organization. {@code eventId} unique là lớp bảo vệ cuối cùng cho claim
 * idempotency.</p>
 */
@Entity
@Table(name = "PAYMENT_RECEIPT", uniqueConstraints = @UniqueConstraint(
        name = "uk_payment_receipt_event_id", columnNames = "event_id"))
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentReceiptJpaEntity {

    @Id
    @Column(name = "receipt_id", nullable = false, updatable = false)
    private UUID receiptId;

    @Column(name = "event_id", nullable = false, updatable = false)
    private UUID eventId;

    @Column(name = "invoice_id", nullable = false, updatable = false)
    private UUID invoiceId;

    @Column(name = "prescription_id", nullable = false, updatable = false)
    private UUID prescriptionId;

    @Column(name = "patient_id", nullable = false, updatable = false)
    private UUID patientId;

    @Column(name = "department_id", nullable = false, updatable = false)
    private UUID departmentId;

    @Column(name = "total_amount", precision = 15, scale = 2, nullable = false, updatable = false)
    private BigDecimal totalAmount;

    @Column(name = "payment_method", length = 50, updatable = false)
    private String paymentMethod;

    @Column(name = "payment_occurred_at", nullable = false, updatable = false)
    private Instant paymentOccurredAt;

    @Column(name = "correlation_id", length = 200, nullable = false, updatable = false)
    private String correlationId;

    @Column(name = "payload_fingerprint", length = 128, updatable = false)
    private String payloadFingerprint;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private PaymentReceiptStatus status;

    @Column(name = "failure_code", length = 100)
    private String failureCode;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
