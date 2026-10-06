package com.mediflow.notification.application.service;

import java.util.UUID;
import com.mediflow.notification.application.dto.command.CarePaymentReceiptCommand;
import com.mediflow.notification.application.port.in.ReactToCarePaymentReceiptUseCase;
import com.mediflow.notification.application.port.out.CareNotificationRepositoryPort;
import com.mediflow.notification.domain.model.CareNotificationIntent;

public class CarePaymentReceiptService implements ReactToCarePaymentReceiptUseCase {
    private final CareNotificationRepositoryPort repository;
    public CarePaymentReceiptService(CareNotificationRepositoryPort repository) { this.repository = repository; }

    @Override public void receive(CarePaymentReceiptCommand receipt) {
        if (!repository.claim(receipt.eventId(), "payment.completed", receipt.fingerprint())) return;
        String template = switch (receipt.classification()) {
            case "ADMISSION_DEPOSIT" -> "ADMISSION_DEPOSIT_RECEIPT";
            case "SETTLEMENT_PAYMENT" -> "SETTLEMENT_PAYMENT_RECEIPT";
            default -> "PAYMENT_RECEIPT";
        };
        String amount = receipt.amount().toPlainString() + " " + receipt.currency();
        String title = "ADMISSION_DEPOSIT".equals(receipt.classification()) ? "Đã nhận tiền cọc" : "Đã ghi nhận thanh toán";
        String content = "ADMISSION_DEPOSIT".equals(receipt.classification())
                ? "Đã ghi nhận tiền cọc " + amount + ". Đây chưa phải số viện phí quyết toán."
                : "Đã ghi nhận khoản thanh toán " + amount + ". Xem tài khoản viện phí để biết số tiền còn lại.";
        repository.deliverInApp(new CareNotificationIntent(UUID.randomUUID(), receipt.patientId(), receipt.eventId(),
                "payment.completed", receipt.transactionId(), receipt.correlationId(), template, title, content));
    }
}
