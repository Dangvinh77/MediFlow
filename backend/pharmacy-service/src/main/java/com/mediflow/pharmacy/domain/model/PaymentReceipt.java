package com.mediflow.pharmacy.domain.model;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;

import com.mediflow.pharmacy.domain.exception.PaymentReceiptRuleException;
import com.mediflow.pharmacy.domain.model.enums.PaymentReceiptStatus;

import lombok.Getter;

/**
 * Bằng chứng thanh toán do billing gửi và pharmacy lưu để resume/idempotency.
 *
 * <p>Aggregate này chỉ giữ các UUID tham chiếu và snapshot payload từ bounded context Billing;
 * nó không tạo quan hệ JPA sang invoice, patient hoặc department. Receipt mới luôn bắt đầu ở
 * {@link PaymentReceiptStatus#RECEIVED}. Trạng thái này có chủ ý không terminal để một lần giao
 * lại sau crash có thể tiếp tục workflow.</p>
 */
@Getter
public final class PaymentReceipt {

    private final UUID receiptId;
    private final UUID eventId;
    private final UUID invoiceId;
    private final UUID prescriptionId;
    private final UUID patientId;
    private final UUID departmentId;
    private final BigDecimal totalAmount;
    private final String paymentMethod;
    private final Instant paymentOccurredAt;
    private final String correlationId;
    private final String payloadFingerprint;
    private PaymentReceiptStatus status;
    private String failureCode;
    private final Instant createdAt;
    private Instant updatedAt;

    private PaymentReceipt(
            UUID receiptId,
            UUID eventId,
            UUID invoiceId,
            UUID prescriptionId,
            UUID patientId,
            UUID departmentId,
            BigDecimal totalAmount,
            String paymentMethod,
            Instant paymentOccurredAt,
            String correlationId,
            String payloadFingerprint,
            PaymentReceiptStatus status,
            String failureCode,
            Instant createdAt,
            Instant updatedAt) {
        this.receiptId = receiptId;
        this.eventId = eventId;
        this.invoiceId = invoiceId;
        this.prescriptionId = prescriptionId;
        this.patientId = patientId;
        this.departmentId = departmentId;
        this.totalAmount = totalAmount;
        this.paymentMethod = paymentMethod;
        this.paymentOccurredAt = paymentOccurredAt;
        this.correlationId = correlationId;
        this.payloadFingerprint = payloadFingerprint;
        this.status = status;
        this.failureCode = failureCode;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    /**
     * Nhận một payment event mới ở trạng thái {@code RECEIVED}.
     *
     * @return receipt chưa có id persistence
     */
    public static PaymentReceipt receive(
            UUID eventId,
            UUID invoiceId,
            UUID prescriptionId,
            UUID patientId,
            UUID departmentId,
            BigDecimal totalAmount,
            String paymentMethod,
            Instant paymentOccurredAt,
            String correlationId,
            String payloadFingerprint) {
        requireIdentifiers(eventId, invoiceId, prescriptionId, patientId, departmentId);
        requireAmount(totalAmount);
        requireInstant(paymentOccurredAt);
        requireText(correlationId, "PAYMENT_RECEIPT_CORRELATION_REQUIRED", "correlationId");
        String normalizedPaymentMethod = normalizeOptional(paymentMethod);
        String normalizedCorrelationId = correlationId.trim();
        String normalizedFingerprint = normalizeOptional(payloadFingerprint);
        if (normalizedFingerprint == null) {
            normalizedFingerprint = fingerprint(
                    eventId, invoiceId, prescriptionId, patientId, departmentId,
                    totalAmount, normalizedPaymentMethod, paymentOccurredAt, normalizedCorrelationId);
        }
        return new PaymentReceipt(
                null, eventId, invoiceId, prescriptionId, patientId, departmentId,
                totalAmount, normalizedPaymentMethod, paymentOccurredAt,
                normalizedCorrelationId, normalizedFingerprint,
                PaymentReceiptStatus.RECEIVED, null, null, null);
    }

    /**
     * Dựng lại aggregate từ persistence, không chạy lại transition.
     *
     * @return receipt phản ánh đúng trạng thái đã lưu
     */
    public static PaymentReceipt restore(
            UUID receiptId,
            UUID eventId,
            UUID invoiceId,
            UUID prescriptionId,
            UUID patientId,
            UUID departmentId,
            BigDecimal totalAmount,
            String paymentMethod,
            Instant paymentOccurredAt,
            String correlationId,
            String payloadFingerprint,
            PaymentReceiptStatus status,
            String failureCode,
            Instant createdAt,
            Instant updatedAt) {
        requireIdentifiers(eventId, invoiceId, prescriptionId, patientId, departmentId);
        requireAmount(totalAmount);
        requireInstant(paymentOccurredAt);
        requireText(correlationId, "PAYMENT_RECEIPT_CORRELATION_REQUIRED", "correlationId");
        if (status == null) {
            throw new PaymentReceiptRuleException(
                    "PAYMENT_RECEIPT_STATUS_REQUIRED", "Trạng thái receipt là bắt buộc");
        }
        return new PaymentReceipt(
                receiptId, eventId, invoiceId, prescriptionId, patientId, departmentId,
                totalAmount, normalizeOptional(paymentMethod), paymentOccurredAt,
                correlationId.trim(), normalizeOptional(payloadFingerprint), status,
                normalizeOptional(failureCode), createdAt, updatedAt);
    }

    /**
     * Đánh dấu workflow xuất thuốc thành công. Gọi lặp lại ở trạng thái DISPENSED là no-op;
     * gọi ngược từ COMPENSATED bị từ chối để không ghi đè terminal outcome.
     *
     * @param timestamp thời điểm hoàn tất workflow
     */
    public void markDispensed(Instant timestamp) {
        if (status == PaymentReceiptStatus.DISPENSED) {
            return;
        }
        requireReceived("DISPENSED");
        if (timestamp == null) {
            throw new PaymentReceiptRuleException(
                    "PAYMENT_RECEIPT_TIME_REQUIRED", "Thời điểm hoàn tất receipt là bắt buộc");
        }
        status = PaymentReceiptStatus.DISPENSED;
        failureCode = null;
        updatedAt = timestamp;
    }

    /**
     * Đánh dấu workflow thất bại và đã phát nhánh bù trừ. Gọi lặp lại với receipt đã COMPENSATED
     * là no-op; mọi attempt ghi đè terminal khác đều bị từ chối.
     *
     * @param code mã lỗi nghiệp vụ ổn định
     * @param timestamp thời điểm ghi nhận bù trừ
     */
    public void markCompensated(String code, Instant timestamp) {
        String normalized = requireText(code, "PAYMENT_RECEIPT_FAILURE_REQUIRED", "failureCode");
        if (status == PaymentReceiptStatus.COMPENSATED) {
            if (!Objects.equals(failureCode, normalized.trim())) {
                throw invalidTransition("COMPENSATED");
            }
            return;
        }
        requireReceived("COMPENSATED");
        if (timestamp == null) {
            throw new PaymentReceiptRuleException(
                    "PAYMENT_RECEIPT_TIME_REQUIRED", "Thời điểm bù trừ receipt là bắt buộc");
        }
        status = PaymentReceiptStatus.COMPENSATED;
        failureCode = normalized.trim();
        updatedAt = timestamp;
    }

    /** @return true nếu receipt đã có outcome terminal */
    public boolean isTerminal() {
        return status == PaymentReceiptStatus.DISPENSED || status == PaymentReceiptStatus.COMPENSATED;
    }

    /**
     * So sánh snapshot payload, dùng để phát hiện cùng event/business key nhưng dữ liệu xung đột.
     * Outcome/persistence timestamps bị bỏ qua; occurredAt so theo precision PostgreSQL lưu được,
     * còn fingerprint giữ phân biệt của timestamp đầy đủ từ event gốc.
     */
    public boolean hasSamePayload(PaymentReceipt other) {
        if (other == null) {
            return false;
        }
        return Objects.equals(eventId, other.eventId)
                && Objects.equals(invoiceId, other.invoiceId)
                && Objects.equals(prescriptionId, other.prescriptionId)
                && Objects.equals(patientId, other.patientId)
                && Objects.equals(departmentId, other.departmentId)
                && totalAmount.compareTo(other.totalAmount) == 0
                && Objects.equals(paymentMethod, other.paymentMethod)
                && postgresTimestampPrecision(paymentOccurredAt)
                        .equals(postgresTimestampPrecision(other.paymentOccurredAt))
                && Objects.equals(correlationId, other.correlationId)
                // Historical rows predate payload_fingerprint. For those rows the comparison
                // can only use values PostgreSQL retained; sub-microsecond source precision is gone.
                && (payloadFingerprint == null || other.payloadFingerprint == null
                        || Objects.equals(payloadFingerprint, other.payloadFingerprint));
    }

    private static String fingerprint(
            UUID eventId,
            UUID invoiceId,
            UUID prescriptionId,
            UUID patientId,
            UUID departmentId,
            BigDecimal totalAmount,
            String paymentMethod,
            Instant paymentOccurredAt,
            String correlationId) {
        String canonicalPayload = canonical(eventId.toString())
                + canonical(invoiceId.toString())
                + canonical(prescriptionId.toString())
                + canonical(patientId.toString())
                + canonical(departmentId.toString())
                + canonical(totalAmount.stripTrailingZeros().toPlainString())
                + canonical(paymentMethod)
                + canonical(paymentOccurredAt.toString())
                + canonical(correlationId);
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonicalPayload.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 must be available in the Java runtime", exception);
        }
    }

    private static String canonical(String value) {
        return value == null ? "-1:" : value.length() + ":" + value;
    }

    private static Instant postgresTimestampPrecision(Instant value) {
        // PostgreSQL TIMESTAMPTZ stores microseconds and rounds fractional precision.
        return value.plusNanos(500).truncatedTo(ChronoUnit.MICROS);
    }

    private void requireReceived(String target) {
        if (status != PaymentReceiptStatus.RECEIVED) {
            throw invalidTransition(target);
        }
    }

    private PaymentReceiptRuleException invalidTransition(String target) {
        return new PaymentReceiptRuleException(
                "PAYMENT_RECEIPT_INVALID_TRANSITION",
                "Không thể chuyển receipt từ " + status + " sang " + target);
    }

    private static void requireIdentifiers(UUID eventId, UUID invoiceId, UUID prescriptionId,
                                          UUID patientId, UUID departmentId) {
        if (eventId == null) throw required("EVENT_ID", "eventId");
        if (invoiceId == null) throw required("INVOICE_ID", "invoiceId");
        if (prescriptionId == null) throw required("PRESCRIPTION_ID", "prescriptionId");
        if (patientId == null) throw required("PATIENT_ID", "patientId");
        if (departmentId == null) throw required("DEPARTMENT_ID", "departmentId");
    }

    private static void requireAmount(BigDecimal amount) {
        if (amount == null || amount.signum() < 0) {
            throw new PaymentReceiptRuleException(
                    "PAYMENT_RECEIPT_AMOUNT_INVALID", "totalAmount phải không âm");
        }
    }

    private static void requireInstant(Instant value) {
        if (value == null) {
            throw required("PAYMENT_TIME", "paymentOccurredAt");
        }
    }

    private static String requireText(String value, String code, String field) {
        if (value == null || value.isBlank()) {
            throw new PaymentReceiptRuleException(code, field + " không được bỏ trống");
        }
        return value;
    }

    private static PaymentReceiptRuleException required(String field, String name) {
        return new PaymentReceiptRuleException(
                "PAYMENT_RECEIPT_" + field + "_REQUIRED", name + " không được null");
    }

    private static String normalizeOptional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
