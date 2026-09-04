package com.mediflow.billing.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.mediflow.billing.domain.exception.BillingRuleException;

import lombok.Getter;

/**
 * Hóa đơn — aggregate root của billing. Có thể là hóa đơn thường (gộp các khoản phí chưa trả của
 * bệnh nhân, {@code sagaStatus = NONE} suốt vòng đời) hoặc hóa đơn mở saga từ một đơn thuốc
 * (kê đơn → hóa đơn → thanh toán → xuất thuốc, xem backend-spec/06-billing.md §3).
 */
@Getter
public class Invoice {

    private final UUID invoiceId;
    private final UUID patientId;
    private final LocalDate createdDate;
    private final BigDecimal totalAmount;
    private boolean isPaid;
    private PaymentMethod paymentMethod;
    private UUID dispenseId;
    private final UUID prescriptionId;
    private SagaStatus sagaStatus;
    private Instant paidAt;
    private final Instant createdAt;
    private Instant updatedAt;

    private Invoice(UUID invoiceId, UUID patientId, LocalDate createdDate, BigDecimal totalAmount,
                     boolean isPaid, PaymentMethod paymentMethod, UUID dispenseId, UUID prescriptionId,
                     SagaStatus sagaStatus, Instant paidAt, Instant createdAt, Instant updatedAt) {
        this.invoiceId = invoiceId;
        this.patientId = patientId;
        this.createdDate = createdDate;
        this.totalAmount = totalAmount;
        this.isPaid = isPaid;
        this.paymentMethod = paymentMethod;
        this.dispenseId = dispenseId;
        this.prescriptionId = prescriptionId;
        this.sagaStatus = sagaStatus;
        this.paidAt = paidAt;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    /** Hóa đơn thường: gộp các khoản phí chưa trả của bệnh nhân, không tham gia saga (BR-B2). */
    public static Invoice create(UUID patientId, LocalDate createdDate, List<Fee> unpaidFees) {
        if (unpaidFees == null || unpaidFees.isEmpty()) {
            throw new BillingRuleException("BILLING_NO_UNPAID_FEES",
                    "Bệnh nhân không có khoản phí nào chưa thanh toán");
        }
        BigDecimal total = sumAmounts(unpaidFees);
        return new Invoice(null, patientId, createdDate, total, false, null, null, null,
                SagaStatus.NONE, null, null, null);
    }

    /**
     * Cửa vào saga — dựng hóa đơn từ một đơn thuốc, bắt đầu thẳng ở {@code AWAITING_PAYMENT}.
     * Đây là nhánh duy nhất được rời {@code NONE} (BR-B9, khác hóa đơn thường ở {@link #create}).
     */
    public static Invoice createFromPrescription(UUID patientId, UUID prescriptionId, List<Fee> fees) {
        if (fees == null || fees.isEmpty()) {
            throw new BillingRuleException("BILLING_NO_UNPAID_FEES",
                    "Không có khoản phí nào để lập hóa đơn từ đơn thuốc");
        }
        BigDecimal total = sumAmounts(fees);
        return new Invoice(null, patientId, LocalDate.now(), total, false, null, null, prescriptionId,
                SagaStatus.AWAITING_PAYMENT, null, null, null);
    }

    /** Dựng lại từ dữ liệu đã lưu — không chạy lại quy tắc lúc tạo. */
    public static Invoice restore(UUID invoiceId, UUID patientId, LocalDate createdDate, BigDecimal totalAmount,
                                   boolean isPaid, PaymentMethod paymentMethod, UUID dispenseId,
                                   UUID prescriptionId, SagaStatus sagaStatus, Instant paidAt,
                                   Instant createdAt, Instant updatedAt) {
        return new Invoice(invoiceId, patientId, createdDate, totalAmount, isPaid, paymentMethod,
                dispenseId, prescriptionId, sagaStatus, paidAt, createdAt, updatedAt);
    }

    /** Thanh toán hóa đơn — ném {@code BILLING_ALREADY_PAID} nếu đã trả trước đó (BR-B1). */
    public void pay(PaymentMethod method, Instant timestamp) {
        if (isAlreadyPaid()) {
            throw new BillingRuleException("BILLING_ALREADY_PAID", "Hóa đơn này đã được thanh toán");
        }
        this.isPaid = true;
        this.paymentMethod = method;
        this.paidAt = timestamp;
    }

    public boolean isAlreadyPaid() {
        return isPaid;
    }

    /** Chuyển trạng thái saga theo đúng máy trạng thái ở §3 — sai thì ném {@code BILLING_INVALID_SAGA_TRANSITION} (BR-B9). */
    public void transitionSaga(SagaStatus next) {
        if (!isValidTransition(sagaStatus, next)) {
            throw new BillingRuleException("BILLING_INVALID_SAGA_TRANSITION",
                    "Không thể chuyển trạng thái saga từ " + sagaStatus + " sang " + next);
        }
        this.sagaStatus = next;
    }

    private static boolean isValidTransition(SagaStatus from, SagaStatus to) {
        return switch (from) {
            case AWAITING_PAYMENT -> to == SagaStatus.PAID;
            case PAID -> to == SagaStatus.AWAITING_DISPENSE;
            case AWAITING_DISPENSE -> to == SagaStatus.COMPLETED || to == SagaStatus.REFUNDED;
            // NONE (hóa đơn thường không tham gia saga), COMPLETED, REFUNDED đều là trạng thái kết thúc
            case NONE, COMPLETED, REFUNDED -> false;
        };
    }

    /** Bù trừ khi xuất thuốc thất bại: đảo lại trạng thái đã trả và kết thúc saga ở REFUNDED (BR-B4). */
    public void refund() {
        this.isPaid = false;
        transitionSaga(SagaStatus.REFUNDED);
    }

    /** Gán phiếu xuất thuốc khi saga hoàn tất thành công (BR-B11). */
    public void assignDispense(UUID dispenseId) {
        this.dispenseId = dispenseId;
    }

    private static BigDecimal sumAmounts(List<Fee> fees) {
        return fees.stream()
                .map(Fee::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }
}
