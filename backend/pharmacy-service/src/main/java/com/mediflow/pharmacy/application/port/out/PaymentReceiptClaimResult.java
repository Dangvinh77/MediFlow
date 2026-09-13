package com.mediflow.pharmacy.application.port.out;

import java.util.Objects;

import com.mediflow.pharmacy.domain.model.PaymentReceipt;

/**
 * Kết quả có nghĩa của thao tác claim receipt.
 *
 * @param status trạng thái claim
 * @param receipt row hiện có hoặc vừa được claim; luôn khác null khi adapter trả về thành công
 */
public record PaymentReceiptClaimResult(
        PaymentReceiptClaimStatus status,
        PaymentReceipt receipt) {

    /** Kiểm tra invariant của kết quả port ngay tại biên application. */
    public PaymentReceiptClaimResult {
        Objects.requireNonNull(status, "status không được null");
        Objects.requireNonNull(receipt, "receipt không được null");
    }

    /** @return true nếu caller là owner của receipt mới */
    public boolean claimed() {
        return status == PaymentReceiptClaimStatus.CLAIMED;
    }

    /** @return true nếu payload mới xung đột với receipt đã lưu */
    public boolean conflict() {
        return status == PaymentReceiptClaimStatus.DUPLICATE_CONFLICT;
    }
}
