package com.mediflow.pharmacy.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.mediflow.pharmacy.domain.exception.PaymentReceiptRuleException;
import com.mediflow.pharmacy.domain.model.enums.PaymentReceiptStatus;

/** Kiểm thử state machine và payload invariant của payment receipt. */
class PaymentReceiptTest {

    private static final Instant OCCURRED_AT = Instant.parse("2026-01-01T10:15:30Z");

    @Test
    void receive_startsInReceivedState() {
        PaymentReceipt receipt = receipt();

        assertThat(receipt.getStatus()).isEqualTo(PaymentReceiptStatus.RECEIVED);
        assertThat(receipt.isTerminal()).isFalse();
    }

    @Test
    void markDispensed_isIdempotentForSameTerminalState() {
        PaymentReceipt receipt = receipt();

        receipt.markDispensed(OCCURRED_AT.plusSeconds(1));
        receipt.markDispensed(OCCURRED_AT.plusSeconds(2));

        assertThat(receipt.getStatus()).isEqualTo(PaymentReceiptStatus.DISPENSED);
        assertThat(receipt.getFailureCode()).isNull();
    }

    @Test
    void markCompensated_isIdempotentForSameCode() {
        PaymentReceipt receipt = receipt();

        receipt.markCompensated("STOCK_UNAVAILABLE", OCCURRED_AT.plusSeconds(1));
        receipt.markCompensated("STOCK_UNAVAILABLE", OCCURRED_AT.plusSeconds(2));

        assertThat(receipt.getStatus()).isEqualTo(PaymentReceiptStatus.COMPENSATED);
        assertThat(receipt.getFailureCode()).isEqualTo("STOCK_UNAVAILABLE");
    }

    @Test
    void terminalState_cannotBeOverwrittenOrReversed() {
        PaymentReceipt dispensed = receipt();
        dispensed.markDispensed(OCCURRED_AT.plusSeconds(1));
        assertThatThrownBy(() -> dispensed.markCompensated(
                "LATE_PAYMENT", OCCURRED_AT.plusSeconds(2)))
                .isInstanceOf(PaymentReceiptRuleException.class)
                .hasMessageContaining("DISPENSED");

        PaymentReceipt compensated = receipt();
        compensated.markCompensated("STOCK_UNAVAILABLE", OCCURRED_AT.plusSeconds(1));
        assertThatThrownBy(() -> compensated.markDispensed(OCCURRED_AT.plusSeconds(2)))
                .isInstanceOf(PaymentReceiptRuleException.class)
                .hasMessageContaining("COMPENSATED");
    }

    @Test
    void samePayload_ignoresOutcomeAndPersistenceTimestamps() {
        PaymentReceipt first = receipt();
        PaymentReceipt second = PaymentReceipt.restore(
                UUID.randomUUID(), first.getEventId(), first.getInvoiceId(), first.getPrescriptionId(),
                first.getPatientId(), first.getDepartmentId(), first.getTotalAmount(),
                first.getPaymentMethod(), first.getPaymentOccurredAt(), first.getCorrelationId(),
                first.getPayloadFingerprint(), PaymentReceiptStatus.DISPENSED, null,
                Instant.now(), Instant.now());

        assertThat(first.hasSamePayload(second)).isTrue();
    }

    @Test
    void sameEvent_differentPayload_isConflict() {
        PaymentReceipt first = receipt();
        PaymentReceipt second = PaymentReceipt.receive(
                first.getEventId(), first.getInvoiceId(), first.getPrescriptionId(), first.getPatientId(),
                first.getDepartmentId(), new BigDecimal("999.99"), first.getPaymentMethod(),
                first.getPaymentOccurredAt(), first.getCorrelationId(), first.getPayloadFingerprint());

        assertThat(first.hasSamePayload(second)).isFalse();
    }

    @Test
    void samePayload_survivesPostgresTimestampPrecision_withoutLosingNanosecondConflictDetection() {
        UUID eventId = UUID.randomUUID();
        UUID invoiceId = UUID.randomUUID();
        UUID prescriptionId = UUID.randomUUID();
        UUID patientId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        Instant occurredAt = Instant.parse("2026-01-01T10:15:30.123456789Z");
        PaymentReceipt received = PaymentReceipt.receive(
                eventId, invoiceId, prescriptionId, patientId, departmentId,
                new BigDecimal("120.00"), "CASH", occurredAt, "corr-precise", null);
        PaymentReceipt restored = PaymentReceipt.restore(
                UUID.randomUUID(), eventId, invoiceId, prescriptionId, patientId, departmentId,
                new BigDecimal("120.00"), "CASH",
                occurredAt.plusNanos(500).truncatedTo(ChronoUnit.MICROS),
                "corr-precise", received.getPayloadFingerprint(), PaymentReceiptStatus.RECEIVED,
                null, OCCURRED_AT, OCCURRED_AT);
        PaymentReceipt conflicting = PaymentReceipt.receive(
                eventId, invoiceId, prescriptionId, patientId, departmentId,
                new BigDecimal("120.00"), "CASH", occurredAt.plusNanos(1), "corr-precise", null);

        assertThat(received.getPayloadFingerprint()).hasSize(64);
        assertThat(received.hasSamePayload(restored)).isTrue();
        assertThat(received.hasSamePayload(conflicting)).isFalse();
    }

    @Test
    void legacyPayloadWithoutFingerprint_comparesOnlyPersistedFields() {
        UUID eventId = UUID.randomUUID();
        UUID invoiceId = UUID.randomUUID();
        UUID prescriptionId = UUID.randomUUID();
        UUID patientId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        Instant persistedAt = Instant.parse("2026-01-01T10:15:30.123457Z");
        PaymentReceipt legacy = PaymentReceipt.restore(
                UUID.randomUUID(), eventId, invoiceId, prescriptionId, patientId, departmentId,
                new BigDecimal("120.00"), "CASH", persistedAt, "corr-legacy", null,
                PaymentReceiptStatus.RECEIVED, null, OCCURRED_AT, OCCURRED_AT);
        PaymentReceipt replay = PaymentReceipt.receive(
                eventId, invoiceId, prescriptionId, patientId, departmentId,
                new BigDecimal("120.00"), "CASH",
                Instant.parse("2026-01-01T10:15:30.123456789Z"), "corr-legacy", null);
        PaymentReceipt persistedConflict = PaymentReceipt.receive(
                eventId, invoiceId, prescriptionId, patientId, departmentId,
                new BigDecimal("121.00"), "CASH", persistedAt, "corr-legacy", null);

        assertThat(legacy.hasSamePayload(replay)).isTrue();
        assertThat(legacy.hasSamePayload(persistedConflict)).isFalse();
    }

    @Test
    void receive_missingCorrelationId_isRejected() {
        assertThatThrownBy(() -> PaymentReceipt.receive(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                BigDecimal.ONE, "CASH", OCCURRED_AT, " ", null))
                .isInstanceOf(PaymentReceiptRuleException.class)
                .hasMessageContaining("correlationId");
    }

    private PaymentReceipt receipt() {
        return PaymentReceipt.receive(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                new BigDecimal("120.00"), "CASH", OCCURRED_AT, "corr-001", "fingerprint-001");
    }
}
