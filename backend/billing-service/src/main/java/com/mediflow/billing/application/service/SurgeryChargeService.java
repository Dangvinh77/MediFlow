package com.mediflow.billing.application.service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mediflow.billing.application.event.LedgerIntegrationEvent;
import com.mediflow.billing.application.event.LedgerIntegrationEvent.PaymentRefundedPayload;
import com.mediflow.billing.application.event.SurgeryCancelledEvent;
import com.mediflow.billing.application.event.SurgeryCaseCreatedEvent;
import com.mediflow.billing.application.event.SurgeryCompletedEvent;
import com.mediflow.billing.application.port.in.SurgeryChargeUseCase;
import com.mediflow.billing.application.port.out.LedgerEventPort;
import com.mediflow.billing.application.port.out.PriceCatalogPort;
import com.mediflow.billing.application.port.out.ProcessedEventPort;
import com.mediflow.billing.application.port.out.SurgeryChargeRepositoryPort;
import com.mediflow.billing.application.port.out.SurgeryChargeRepositoryPort.ChargeAllocation;
import com.mediflow.billing.domain.exception.BillingRuleException;
import com.mediflow.billing.domain.model.BillingAccount;
import com.mediflow.billing.domain.model.CareEpisodeType;
import com.mediflow.billing.domain.model.Charge;
import com.mediflow.billing.domain.model.PaymentClassification;
import com.mediflow.billing.domain.model.PaymentTransaction;
import com.mediflow.billing.domain.model.PaymentTransactionType;

/**
 * Hiện thực {@link SurgeryChargeUseCase} — tạo/đối chiếu/hủy charge ca mổ
 * (CONTRACT-SURGERY-BILLING-01). Charge dùng sổ V2 ledger ({@link Charge}/{@link BillingAccount}),
 * khác sổ FEE/INVOICE mà {@link FeeAccrualService} dùng cho EXAM/LAB/DRUG.
 *
 * <p>Mọi handler: chống xử lý trùng theo {@code eventId} ({@link ProcessedEventPort}), cộng thêm
 * khóa nghiệp vụ riêng — charge dedup theo {@code (sourceType, sourceId, priceCode)}, đối chiếu
 * theo {@code resultId} bất biến, hoàn tiền theo idempotency key dẫn xuất từ
 * {@code cancellationId}. Mã giá chưa khai báo ({@link PriceCatalogPort}) bị từ chối, không mặc
 * định về 0 — lỗi được ném tiếp để message vào DLQ, không bị nuốt.
 */
@Service
public class SurgeryChargeService implements SurgeryChargeUseCase {

    private static final String SOURCE_TYPE_SURGERY = "SURGERY";
    private static final String RK_SURGERY_CASE_CREATED = "surgery.case.created";
    private static final String RK_SURGERY_COMPLETED = "surgery.completed";
    private static final String RK_SURGERY_CANCELLED = "surgery.cancelled";
    private static final String CURRENCY = "VND";

    private final ProcessedEventPort processedEvent;
    private final SurgeryChargeRepositoryPort repository;
    private final PriceCatalogPort priceCatalog;
    private final LedgerEventPort events;

    public SurgeryChargeService(ProcessedEventPort processedEvent, SurgeryChargeRepositoryPort repository,
                                PriceCatalogPort priceCatalog, LedgerEventPort events) {
        this.processedEvent = processedEvent;
        this.repository = repository;
        this.priceCatalog = priceCatalog;
        this.events = events;
    }

    /** {@code surgery.case.created} → charge dự kiến (POSTED) cho từng dòng {@code plannedItems}. */
    @Override
    @Transactional
    public void onSurgeryCaseCreated(SurgeryCaseCreatedEvent e) {
        if (processedEvent.alreadyProcessed(e.eventId())) {
            return;
        }
        CareEpisodeType episodeType = parseEpisodeType(e.careEpisodeType());
        BillingAccount account = repository.findOrOpenAccount(e.patientId(), e.departmentId(), episodeType,
                e.careEpisodeId(), CURRENCY, e.occurredAt());
        for (SurgeryCaseCreatedEvent.PlannedItem item : e.plannedItems()) {
            var price = priceCatalog.requireActive(item.priceCode(), e.occurredAt());
            Optional<Charge> existing = repository.findChargeBySource(SOURCE_TYPE_SURGERY, e.surgeryCaseId(), item.priceCode());
            if (existing.isPresent()) {
                require(existing.get().getQuantity().compareTo(item.quantity()) == 0, "BILLING_SURGERY_CHARGE_CONFLICT",
                        "Kế hoạch mổ gửi lại khác số liệu đã ghi cho price code " + item.priceCode());
                continue;
            }
            Charge charge = Charge.post(account.getAccountId(), e.patientId(), e.departmentId(), SOURCE_TYPE_SURGERY,
                    e.surgeryCaseId(), item.priceCode(), price.description(), item.quantity(), price.unitAmount(), e.occurredAt());
            repository.saveCharge(charge);
        }
        processedEvent.markProcessed(e.eventId(), RK_SURGERY_CASE_CREATED);
    }

    /** {@code surgery.completed} → đối chiếu charge theo {@code performedItems} thực tế đã mổ. */
    @Override
    @Transactional
    public void onSurgeryCompleted(SurgeryCompletedEvent e) {
        if (processedEvent.alreadyProcessed(e.eventId())) {
            return;
        }
        List<Charge> existingCharges = repository.findChargesBySource(SOURCE_TYPE_SURGERY, e.surgeryCaseId());
        for (SurgeryCompletedEvent.PerformedItem item : e.performedItems()) {
            var price = priceCatalog.requireActive(item.priceCode(), e.recordedAt());
            Optional<Charge> existing = existingCharges.stream()
                    .filter(c -> c.getPriceCode().equals(item.priceCode())).findFirst();
            if (existing.isPresent()) {
                Charge charge = existing.get();
                charge.reconcilePerformed(e.resultId(), item.quantity(), price.unitAmount());
                repository.updateCharge(charge);
            } else {
                // Dòng thực tế không có trong kế hoạch (ví dụ EXTRA_ITEM) — cần ít nhất một charge
                // đã có cho case này để biết account; nếu chưa có, surgery.case.created chưa tới.
                UUID accountId = existingCharges.stream().findFirst().map(Charge::getAccountId)
                        .orElseThrow(() -> new BillingRuleException("BILLING_SURGERY_CASE_NOT_FOUND",
                                "Chưa có charge dự kiến cho ca mổ " + e.surgeryCaseId() + "; chờ surgery.case.created"));
                Charge charge = Charge.post(accountId, e.patientId(), e.departmentId(), SOURCE_TYPE_SURGERY,
                        e.surgeryCaseId(), item.priceCode(), price.description(), item.quantity(), price.unitAmount(),
                        e.recordedAt());
                charge.reconcilePerformed(e.resultId(), item.quantity(), price.unitAmount());
                repository.saveCharge(charge);
            }
        }
        processedEvent.markProcessed(e.eventId(), RK_SURGERY_COMPLETED);
    }

    /** {@code surgery.cancelled} → hủy charge chưa phân bổ thanh toán, hoàn tiền phần đã thanh toán. */
    @Override
    @Transactional
    public void onSurgeryCancelled(SurgeryCancelledEvent e) {
        if (processedEvent.alreadyProcessed(e.eventId())) {
            return;
        }
        for (Charge charge : repository.findChargesBySource(SOURCE_TYPE_SURGERY, e.surgeryCaseId())) {
            if (!charge.isPosted()) {
                continue;
            }
            for (ChargeAllocation allocation : repository.findCompletedAllocations(charge.getChargeId())) {
                refundAllocation(e, charge, allocation);
            }
            charge.voidCharge("Hủy ca mổ trước khi bắt đầu: " + e.reason());
            repository.updateCharge(charge);
        }
        processedEvent.markProcessed(e.eventId(), RK_SURGERY_CANCELLED);
    }

    private void refundAllocation(SurgeryCancelledEvent e, Charge charge, ChargeAllocation allocation) {
        String idempotencyKey = "SURGERY_CANCEL:" + e.cancellationId() + ":" + allocation.transactionId();
        if (repository.refundTransactionExists(idempotencyKey)) {
            return;
        }
        BillingAccount account = repository.findAccountById(charge.getAccountId())
                .orElseThrow(() -> new BillingRuleException("BILLING_ACCOUNT_NOT_FOUND",
                        "Không tìm thấy tài khoản cho charge " + charge.getChargeId()));
        PaymentTransaction refund = PaymentTransaction.open(charge.getAccountId(), allocation.paymentRequestId(),
                PaymentTransactionType.REFUND, PaymentClassification.SERVICE_PAYMENT, allocation.amount(),
                allocation.currency(), allocation.paymentMethod(), null, idempotencyKey,
                allocation.transactionId(), e.cancelledAt());
        refund.complete(e.cancelledAt());
        repository.saveRefund(refund, charge.getChargeId());
        events.appendHeld(charge.getAccountId(), new LedgerIntegrationEvent(UUID.randomUUID(), "payment.refunded", 1,
                e.cancelledAt(), e.correlationId(), "billing-service", new PaymentRefundedPayload(
                        refund.getTransactionId(), allocation.transactionId(), charge.getAccountId(), charge.getPatientId(),
                        charge.getDepartmentId(), account.getCareEpisodeType().name(), account.getCareEpisodeId(),
                        allocation.amount(), allocation.currency(), e.reason(), e.cancelledAt())));
    }

    private static CareEpisodeType parseEpisodeType(String value) {
        try {
            return CareEpisodeType.valueOf(value);
        } catch (IllegalArgumentException | NullPointerException invalid) {
            throw new BillingRuleException("BILLING_SURGERY_EPISODE_TYPE_INVALID",
                    "careEpisodeType không hợp lệ: " + value);
        }
    }

    private static void require(boolean valid, String code, String message) {
        if (!valid) {
            throw new BillingRuleException(code, message);
        }
    }
}
