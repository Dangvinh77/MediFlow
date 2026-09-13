package com.mediflow.pharmacy.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.mediflow.pharmacy.application.port.out.PaymentReceiptClaimStatus;
import com.mediflow.pharmacy.domain.model.PaymentReceipt;
import com.mediflow.pharmacy.infrastructure.persistence.repository.PaymentReceiptJpaRepository;

/**
 * PostgreSQL integration tests cho unique event claim và payload conflict.
 *
 * <p>Test được skip có chủ ý khi Docker không chạy; không dùng H2 vì semantics
 * {@code ON CONFLICT} phải được kiểm chứng trên PostgreSQL thật.</p>
 */
@DataJpaTest
@Import(PaymentReceiptPersistenceAdapter.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
class PaymentReceiptPersistenceIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private PaymentReceiptPersistenceAdapter adapter;

    @Autowired
    private PaymentReceiptJpaRepository repository;

    @Test
    void freshInsert_isReadableAndStartsReceived() {
        PaymentReceipt receipt = receipt();

        var result = adapter.claim(receipt);

        assertThat(result.status()).isEqualTo(PaymentReceiptClaimStatus.CLAIMED);
        assertThat(adapter.findByEventId(receipt.getEventId())).isPresent()
                .get().extracting(PaymentReceipt::getStatus)
                .isEqualTo(com.mediflow.pharmacy.domain.model.enums.PaymentReceiptStatus.RECEIVED);
    }

    @Test
    void duplicateEvent_samePayload_isNotClaimedTwice() {
        PaymentReceipt receipt = receipt();
        adapter.claim(receipt);

        assertThat(adapter.claim(receipt).status())
                .isEqualTo(PaymentReceiptClaimStatus.DUPLICATE_SAME);
        assertThat(repository.count()).isEqualTo(1);
    }

    @Test
    void duplicateEvent_differentPayload_isConflict() {
        PaymentReceipt receipt = receipt();
        adapter.claim(receipt);
        PaymentReceipt conflicting = PaymentReceipt.receive(
                receipt.getEventId(), receipt.getInvoiceId(), receipt.getPrescriptionId(),
                receipt.getPatientId(), receipt.getDepartmentId(), new BigDecimal("999.00"),
                receipt.getPaymentMethod(), receipt.getPaymentOccurredAt(), receipt.getCorrelationId(),
                receipt.getPayloadFingerprint());

        assertThat(adapter.claim(conflicting).status())
                .isEqualTo(PaymentReceiptClaimStatus.DUPLICATE_CONFLICT);
        assertThat(repository.count()).isEqualTo(1);
    }

    private PaymentReceipt receipt() {
        return PaymentReceipt.receive(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                new BigDecimal("120.00"), "CASH", Instant.parse("2026-01-01T10:15:30Z"),
                "corr-" + UUID.randomUUID(), "fingerprint-001");
    }
}
