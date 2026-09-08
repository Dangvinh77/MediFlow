package com.mediflow.billing.infrastructure.persistence.jpaEntity;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import com.mediflow.billing.domain.model.PaymentMethod;
import com.mediflow.billing.domain.model.SagaStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * JPA entity của bảng {@code INVOICE} — hóa đơn, aggregate root của billing. Máy trạng thái saga
 * và mọi bất biến (BR-B1..B3, B9) sống trong {@code domain/model/Invoice}; entity chỉ phản ánh
 * lược đồ {@code V1__init.sql}.
 *
 * <p>{@code prescription_id} có unique partial index {@code uq_invoice_prescription} — mỗi đơn thuốc
 * tối đa một hóa đơn (BR-B6). Không có quan hệ JPA sang {@code FEE}: quan hệ hai aggregate được nối
 * bằng cột {@code fee.invoice_id} và truy vấn tường minh trong adapter.
 */
@Entity
@Table(name = "INVOICE")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InvoiceJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "invoice_id", updatable = false, nullable = false)
    private UUID invoiceId;

    @Column(name = "patient_id", nullable = false)
    private UUID patientId;

    @Column(name = "created_date", nullable = false)
    private LocalDate createdDate;

    @Column(name = "total_amount", precision = 15, scale = 2, nullable = false)
    private BigDecimal totalAmount;

    @Column(name = "is_paid", nullable = false)
    private boolean isPaid;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", length = 20)
    private PaymentMethod paymentMethod;

    @Column(name = "dispense_id")
    private UUID dispenseId;

    @Column(name = "prescription_id")
    private UUID prescriptionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "saga_status", length = 20, nullable = false)
    private SagaStatus sagaStatus;

    @Column(name = "paid_at")
    private Instant paidAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
