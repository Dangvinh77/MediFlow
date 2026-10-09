package com.mediflow.billing.application.service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.TreeMap;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mediflow.billing.application.event.SurgeryCancelledEvent;
import com.mediflow.billing.application.event.SurgeryCaseCreatedEvent;
import com.mediflow.billing.application.event.SurgeryCompletedEvent;
import com.mediflow.billing.application.port.in.SurgeryChargeUseCase;
import com.mediflow.billing.application.port.out.LedgerEventPort;
import com.mediflow.billing.application.port.out.PriceCatalogPort;
import com.mediflow.billing.application.port.out.ProcessedEventPort;
import com.mediflow.billing.application.port.out.SurgeryChargeRepositoryPort;
import com.mediflow.billing.domain.exception.BillingRuleException;
import com.mediflow.billing.domain.model.BillingAccount;
import com.mediflow.billing.domain.model.CareEpisodeType;
import com.mediflow.billing.domain.model.Charge;

/**
 * Hiện thực {@link SurgeryChargeUseCase} — tạo/đối chiếu/hủy charge ca mổ
 * (CONTRACT-SURGERY-BILLING-01). Charge dùng sổ V2 ledger ({@link Charge}/{@link BillingAccount}),
 * khác sổ FEE/INVOICE mà {@link FeeAccrualService} dùng cho EXAM/LAB/DRUG.
 *
 * <p>Mọi handler: chống xử lý trùng theo {@code eventId} ({@link ProcessedEventPort}), cộng thêm
 * khóa nghiệp vụ riêng — charge dedup theo {@code (sourceType, sourceId, priceCode)}, đối chiếu
 * theo {@code resultId} bất biến. Paid cancellation uses the separately gated audited adjustment
 * workflow; this compatibility handler cannot fabricate a completed refund. Mã giá chưa khai báo ({@link PriceCatalogPort}) bị từ chối, không mặc
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

    public SurgeryChargeService(ProcessedEventPort processedEvent, SurgeryChargeRepositoryPort repository,
                                PriceCatalogPort priceCatalog, LedgerEventPort events) {
        this.processedEvent = processedEvent;
        this.repository = repository;
        this.priceCatalog = priceCatalog;
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
        require(account.getPatientId().equals(e.patientId()) && account.getCareEpisodeType() == episodeType
                && account.getCareEpisodeId().equals(e.careEpisodeId()), "BILLING_SURGERY_EPISODE_MISMATCH", "Exact episode account required");
        var planned = new TreeMap<String, BigDecimal>();
        var itemCodes = new HashSet<String>();
        require(e.plannedItems() != null && !e.plannedItems().isEmpty() && e.plannedItems().size() <= 1000, "BILLING_SURGERY_ITEMS_REQUIRED", "Planned items required");
        for (var item : e.plannedItems()) {
            require(item != null && code(item.itemCode()) && itemCodes.add(item.itemCode())
                    && code(item.priceCode()) && quantity(item.quantity()),
                    "BILLING_SURGERY_ITEM_INVALID", "Distinct positive planned items required");
            planned.merge(item.priceCode(), item.quantity(), BigDecimal::add);
        }
        for (var item : planned.entrySet()) {
            require(quantity(item.getValue()), "BILLING_CHARGE_INVALID_QUANTITY", "Grouped quantity must fit storage exactly");
            Optional<Charge> existing = repository.findChargeBySource(SOURCE_TYPE_SURGERY, e.surgeryCaseId(), item.getKey());
            if (existing.isPresent()) {
                require(existing.get().getPatientId().equals(e.patientId()) && existing.get().getAccountId().equals(account.getAccountId())
                        && existing.get().getQuantity().compareTo(item.getValue()) == 0, "BILLING_SURGERY_CHARGE_CONFLICT",
                        "Kế hoạch mổ gửi lại khác số liệu đã ghi");
                continue;
            }
            var price = priceCatalog.requireActive(item.getKey(), e.occurredAt());
            Charge charge = Charge.post(account.getAccountId(), e.patientId(), e.departmentId(), SOURCE_TYPE_SURGERY,
                    e.surgeryCaseId(), item.getKey(), price.description(), item.getValue(), price.unitAmount(), e.occurredAt());
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
        require(!existingCharges.isEmpty(), "BILLING_SURGERY_CASE_NOT_FOUND", "Planned source must arrive first");
        var accountId = existingCharges.getFirst().getAccountId();
        for (var charge : existingCharges) require(charge.getPatientId().equals(e.patientId())
                && charge.getDepartmentId().equals(e.departmentId()) && charge.getAccountId().equals(accountId)
                && charge.isPosted(), "BILLING_SURGERY_RECONCILIATION_CONTEXT_MISMATCH", "Exact posted case context required");
        var performed = new TreeMap<String, BigDecimal>();
        var itemIds = new HashSet<UUID>();
        var itemCodes = new HashSet<String>();
        require(e.performedItems() != null && !e.performedItems().isEmpty() && e.performedItems().size() <= 1000, "BILLING_SURGERY_ITEMS_REQUIRED", "Performed items required");
        for (var item : e.performedItems()) {
            require(item != null && item.performedItemId() != null && itemIds.add(item.performedItemId())
                    && code(item.itemCode()) && itemCodes.add(item.itemCode()) && code(item.priceCode())
                    && quantity(item.quantity()),
                    "BILLING_SURGERY_ITEM_INVALID", "Distinct positive performed items required");
            performed.merge(item.priceCode(), item.quantity(), BigDecimal::add);
        }
        for (var item : performed.entrySet()) {
            require(quantity(item.getValue()), "BILLING_CHARGE_INVALID_QUANTITY", "Grouped quantity must fit storage exactly");
            Optional<Charge> existing = existingCharges.stream()
                    .filter(c -> c.getPriceCode().equals(item.getKey())).findFirst();
            if (existing.isPresent()) {
                Charge charge = existing.get();
                // Matched immutable replay must not consult today's catalog or re-price historical care.
                var unit = charge.getReconciledResultId() == null
                        ? priceCatalog.requireActive(item.getKey(), e.recordedAt()).unitAmount() : charge.getUnitAmount();
                charge.reconcilePerformed(e.resultId(), item.getValue(), unit);
                repository.updateCharge(charge);
            } else {
                // Dòng thực tế không có trong kế hoạch (ví dụ EXTRA_ITEM) — cần ít nhất một charge
                // đã có cho case này để biết account; nếu chưa có, surgery.case.created chưa tới.
                var price = priceCatalog.requireActive(item.getKey(), e.recordedAt());
                Charge charge = Charge.post(accountId, e.patientId(), e.departmentId(), SOURCE_TYPE_SURGERY,
                        e.surgeryCaseId(), item.getKey(), price.description(), item.getValue(), price.unitAmount(),
                        e.recordedAt());
                charge.reconcilePerformed(e.resultId(), item.getValue(), price.unitAmount());
                repository.saveCharge(charge);
            }
        }
        processedEvent.markProcessed(e.eventId(), RK_SURGERY_COMPLETED);
    }

    /** Compatibility only: unpaid voiding. Paid cancellation requires the audited strict adjustment path. */
    @Override
    @Transactional
    public void onSurgeryCancelled(SurgeryCancelledEvent e) {
        if (processedEvent.alreadyProcessed(e.eventId())) {
            return;
        }
        require(e.cancellationStage() != null && java.util.Set.of("BEFORE_PREOP", "AFTER_PREOP", "BEFORE_START").contains(e.cancellationStage()),
                "BILLING_SURGERY_CANCELLATION_STAGE_INVALID", "Only pre-start cancellation is supported");
        var charges = repository.findChargesBySource(SOURCE_TYPE_SURGERY, e.surgeryCaseId());
        require(!charges.isEmpty(), "BILLING_SURGERY_CASE_NOT_FOUND", "Planned source must arrive first");
        // Validate the entire case BEFORE effects. A cancellation is never proof that cash was returned.
        for (var charge : charges) {
            require(charge.getReconciledResultId() == null, "BILLING_SURGERY_ALREADY_PERFORMED_OR_VOIDED", "Performed care cannot be cancelled");
            require(repository.findCompletedAllocations(charge.getChargeId()).isEmpty(),
                    "BILLING_SURGERY_CANCELLATION_REQUIRES_ADJUSTMENT", "Paid cancellation requires the strict adjustment workflow");
        }
        for (Charge charge : charges) {
            if (!charge.isPosted()) {
                continue;
            }
            charge.voidCharge("PRE_START_CANCELLATION");
            repository.updateCharge(charge);
        }
        processedEvent.markProcessed(e.eventId(), RK_SURGERY_CANCELLED);
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
    private static boolean code(String value) { return value != null && value.matches("[A-Za-z0-9._-]{1,64}"); }
    private static boolean quantity(BigDecimal value) {
        return value != null && value.signum() > 0 && value.stripTrailingZeros().scale() <= 4
                && value.precision() - value.scale() <= 15;
    }
}
