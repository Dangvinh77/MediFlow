package com.mediflow.pharmacy.infrastructure.persistence.adapter;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.mediflow.pharmacy.application.port.out.PaymentReceiptClaimResult;
import com.mediflow.pharmacy.application.port.out.PaymentReceiptClaimStatus;
import com.mediflow.pharmacy.application.port.out.PaymentReceiptRepositoryPort;
import com.mediflow.pharmacy.domain.model.PaymentReceipt;
import com.mediflow.pharmacy.infrastructure.persistence.jpaEntity.PaymentReceiptJpaEntity;
import com.mediflow.pharmacy.infrastructure.persistence.repository.PaymentReceiptJpaRepository;

import lombok.RequiredArgsConstructor;

/**
 * Adapter persistence cho payment receipt.
 *
 * <p>{@link #claim(PaymentReceipt)} dùng một INSERT ... ON CONFLICT duy nhất để hai consumer
 * đồng thời không cùng thắng. Sau khi claim bị trùng, payload được đọc lại và so sánh ở domain;
 * receipt terminal không bao giờ bị ghi đè bởi event giao lại.</p>
 */
@Component
@RequiredArgsConstructor
public class PaymentReceiptPersistenceAdapter implements PaymentReceiptRepositoryPort {

    private final PaymentReceiptJpaRepository jpaRepository;

    /** {@inheritDoc} */
    @Override
    @Transactional
    public PaymentReceiptClaimResult claim(PaymentReceipt receipt) {
        UUID receiptId = receipt.getReceiptId() == null
                ? UUID.randomUUID() : receipt.getReceiptId();
        int inserted = jpaRepository.insertIfAbsent(
                receiptId,
                receipt.getEventId(),
                receipt.getInvoiceId(),
                receipt.getPrescriptionId(),
                receipt.getPatientId(),
                receipt.getDepartmentId(),
                receipt.getTotalAmount(),
                receipt.getPaymentMethod(),
                receipt.getPaymentOccurredAt(),
                receipt.getCorrelationId(),
                receipt.getPayloadFingerprint());

        PaymentReceipt current = jpaRepository.findByEventId(receipt.getEventId())
                .map(this::toDomain)
                .orElseThrow(() -> new IllegalStateException(
                        "Không đọc được payment receipt sau thao tác claim atomic"));
        if (inserted == 1) {
            return new PaymentReceiptClaimResult(PaymentReceiptClaimStatus.CLAIMED, current);
        }
        PaymentReceiptClaimStatus status = receipt.hasSamePayload(current)
                ? PaymentReceiptClaimStatus.DUPLICATE_SAME
                : PaymentReceiptClaimStatus.DUPLICATE_CONFLICT;
        return new PaymentReceiptClaimResult(status, current);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public Optional<PaymentReceipt> findByEventId(UUID eventId) {
        return jpaRepository.findByEventId(eventId).map(this::toDomain);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public List<PaymentReceipt> findByPrescriptionId(UUID prescriptionId) {
        return jpaRepository.findByPrescriptionIdOrderByCreatedAtAsc(prescriptionId)
                .stream().map(this::toDomain).toList();
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public PaymentReceipt save(PaymentReceipt receipt) {
        PaymentReceiptJpaEntity saved = jpaRepository.saveAndFlush(toEntity(receipt));
        return toDomain(saved);
    }

    private PaymentReceipt toDomain(PaymentReceiptJpaEntity entity) {
        return PaymentReceipt.restore(
                entity.getReceiptId(), entity.getEventId(), entity.getInvoiceId(),
                entity.getPrescriptionId(), entity.getPatientId(), entity.getDepartmentId(),
                entity.getTotalAmount(), entity.getPaymentMethod(), entity.getPaymentOccurredAt(),
                entity.getCorrelationId(), entity.getPayloadFingerprint(), entity.getStatus(),
                entity.getFailureCode(), entity.getCreatedAt(), entity.getUpdatedAt());
    }

    private PaymentReceiptJpaEntity toEntity(PaymentReceipt receipt) {
        return PaymentReceiptJpaEntity.builder()
                .receiptId(receipt.getReceiptId() == null ? UUID.randomUUID() : receipt.getReceiptId())
                .eventId(receipt.getEventId())
                .invoiceId(receipt.getInvoiceId())
                .prescriptionId(receipt.getPrescriptionId())
                .patientId(receipt.getPatientId())
                .departmentId(receipt.getDepartmentId())
                .totalAmount(receipt.getTotalAmount())
                .paymentMethod(receipt.getPaymentMethod())
                .paymentOccurredAt(receipt.getPaymentOccurredAt())
                .correlationId(receipt.getCorrelationId())
                .payloadFingerprint(receipt.getPayloadFingerprint())
                .status(receipt.getStatus())
                .failureCode(receipt.getFailureCode())
                .createdAt(receipt.getCreatedAt())
                .updatedAt(receipt.getUpdatedAt())
                .build();
    }
}
