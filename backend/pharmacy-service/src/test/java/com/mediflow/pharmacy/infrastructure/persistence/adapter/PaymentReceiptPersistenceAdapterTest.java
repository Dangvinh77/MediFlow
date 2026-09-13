package com.mediflow.pharmacy.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.mediflow.pharmacy.application.port.out.PaymentReceiptClaimStatus;
import com.mediflow.pharmacy.domain.model.PaymentReceipt;
import com.mediflow.pharmacy.infrastructure.persistence.jpaEntity.PaymentReceiptJpaEntity;
import com.mediflow.pharmacy.infrastructure.persistence.repository.PaymentReceiptJpaRepository;

/** Kiểm tra adapter phân loại atomic claim và payload conflict. */
class PaymentReceiptPersistenceAdapterTest {

    private final PaymentReceiptJpaRepository repository = mock(PaymentReceiptJpaRepository.class);
    private final PaymentReceiptPersistenceAdapter adapter = new PaymentReceiptPersistenceAdapter(repository);

    @Test
    void claim_inserted_returnsClaimed() {
        PaymentReceipt input = receipt();
        when(repository.insertIfAbsent(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq(input.getEventId()),
                org.mockito.ArgumentMatchers.eq(input.getInvoiceId()),
                org.mockito.ArgumentMatchers.eq(input.getPrescriptionId()),
                org.mockito.ArgumentMatchers.eq(input.getPatientId()),
                org.mockito.ArgumentMatchers.eq(input.getDepartmentId()),
                org.mockito.ArgumentMatchers.eq(input.getTotalAmount()),
                org.mockito.ArgumentMatchers.eq(input.getPaymentMethod()),
                org.mockito.ArgumentMatchers.eq(input.getPaymentOccurredAt()),
                org.mockito.ArgumentMatchers.eq(input.getCorrelationId()),
                org.mockito.ArgumentMatchers.eq(input.getPayloadFingerprint())))
                .thenReturn(1);
        when(repository.findByEventId(input.getEventId())).thenReturn(java.util.Optional.of(entity(input)));

        assertThat(adapter.claim(input).status()).isEqualTo(PaymentReceiptClaimStatus.CLAIMED);
    }

    @Test
    void claim_duplicateSamePayload_returnsDuplicateSame() {
        PaymentReceipt input = receipt();
        when(repository.insertIfAbsent(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any())).thenReturn(0);
        when(repository.findByEventId(input.getEventId())).thenReturn(java.util.Optional.of(entity(input)));

        assertThat(adapter.claim(input).status()).isEqualTo(PaymentReceiptClaimStatus.DUPLICATE_SAME);
    }

    @Test
    void claim_duplicateDifferentPayload_returnsConflict() {
        PaymentReceipt input = receipt();
        PaymentReceipt stored = PaymentReceipt.receive(
                input.getEventId(), input.getInvoiceId(), input.getPrescriptionId(), input.getPatientId(),
                input.getDepartmentId(), new BigDecimal("999.00"), input.getPaymentMethod(),
                input.getPaymentOccurredAt(), input.getCorrelationId(), input.getPayloadFingerprint());
        when(repository.insertIfAbsent(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any())).thenReturn(0);
        when(repository.findByEventId(input.getEventId())).thenReturn(java.util.Optional.of(entity(stored)));

        assertThat(adapter.claim(input).status()).isEqualTo(PaymentReceiptClaimStatus.DUPLICATE_CONFLICT);
    }

    private PaymentReceipt receipt() {
        return PaymentReceipt.receive(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                new BigDecimal("120.00"), "CASH", Instant.parse("2026-01-01T10:15:30Z"),
                "corr-001", "fingerprint-001");
    }

    private PaymentReceiptJpaEntity entity(PaymentReceipt receipt) {
        return PaymentReceiptJpaEntity.builder()
                .receiptId(UUID.randomUUID())
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
                .createdAt(Instant.now())
                .build();
    }
}
