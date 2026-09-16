package com.mediflow.billing.application.service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mediflow.billing.application.event.PaymentFailedEvent;
import com.mediflow.billing.application.event.PrescriptionCancelledEvent;
import com.mediflow.billing.application.event.PrescriptionDispenseFailedEvent;
import com.mediflow.billing.application.event.PrescriptionExpiredEvent;
import com.mediflow.billing.application.event.PrescriptionFilledEvent;
import com.mediflow.billing.application.port.in.SagaCompensationUseCase;
import com.mediflow.billing.application.port.out.BillingEventPublisherPort;
import com.mediflow.billing.application.port.out.FeeRepositoryPort;
import com.mediflow.billing.application.port.out.InvoiceRepositoryPort;
import com.mediflow.billing.application.port.out.ProcessedEventPort;
import com.mediflow.billing.domain.model.Fee;
import com.mediflow.billing.domain.model.Invoice;
import com.mediflow.billing.domain.model.SagaStatus;

/**
 * Hiện thực {@link SagaCompensationUseCase} — hai nhánh kết thúc của saga kê đơn → hóa đơn →
 * thanh toán → xuất thuốc (backend-spec/06-billing.md §3, §7). Do consumer
 * {@code prescription.filled} / {@code prescription.dispense.failed} gọi vào; cả hai idempotent
 * theo {@code eventId}.
 */
@Service
public class SagaCompensationService implements SagaCompensationUseCase {

    private static final Logger log = LoggerFactory.getLogger(SagaCompensationService.class);

    private static final String RK_DISPENSE_FAILED = "prescription.dispense.failed";
    private static final String RK_PRESCRIPTION_FILLED = "prescription.filled";
    private static final String RK_PRESCRIPTION_CANCELLED = "prescription.cancelled";
    private static final String RK_PRESCRIPTION_EXPIRED = "prescription.expired";

    private final ProcessedEventPort processedEvent;
    private final InvoiceRepositoryPort invoiceRepo;
    private final FeeRepositoryPort feeRepo;
    private final BillingEventPublisherPort eventPublisher;

    public SagaCompensationService(ProcessedEventPort processedEvent, InvoiceRepositoryPort invoiceRepo,
                                   FeeRepositoryPort feeRepo, BillingEventPublisherPort eventPublisher) {
        this.processedEvent = processedEvent;
        this.invoiceRepo = invoiceRepo;
        this.feeRepo = feeRepo;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Nhánh <b>bù trừ</b> (BR-B4/BR-B5): xuất thuốc thất bại → đảo thanh toán hóa đơn
     * ({@code isPaid = false}), chuyển saga sang {@code REFUNDED}, {@code refund()} mọi khoản phí
     * đính kèm, rồi publish {@code payment.failed} để notification báo bệnh nhân. Không có hóa đơn
     * tương ứng thì ghi log và bỏ qua (không có gì để bù).
     *
     * <p>Tiền được <i>đảo trong sổ sách</i>, không hoàn qua cổng thanh toán (§7).
     */
    @Override
    @Transactional
    public void onDispenseFailed(PrescriptionDispenseFailedEvent e) {
        if (processedEvent.alreadyProcessed(e.eventId())) {
            return;
        }
        Optional<Invoice> found = invoiceRepo.findByPrescriptionForUpdate(e.prescriptionId());
        if (found.isEmpty()) {
            log.warn("Bỏ qua bù trừ: không có hóa đơn cho prescriptionId={} (event {})",
                    e.prescriptionId(), e.eventId());
            processedEvent.markProcessed(e.eventId(), RK_DISPENSE_FAILED);
            return;
        }

        Invoice invoice = found.get();
        invoice.refund();   // isPaid = false, transitionSaga(REFUNDED)

        List<Fee> fees = feeRepo.findByInvoice(invoice.getInvoiceId());
        fees.forEach(Fee::refund);
        feeRepo.saveAll(fees);
        invoiceRepo.save(invoice);

        eventPublisher.publishPaymentFailed(new PaymentFailedEvent(
                UUID.randomUUID(), Instant.now(), e.correlationId(),
                invoice.getInvoiceId(), invoice.getPatientId(), e.reason()));

        processedEvent.markProcessed(e.eventId(), RK_DISPENSE_FAILED);
    }

    /**
     * Nhánh <b>thành công</b> (BR-B11): xuất thuốc xong → chuyển saga của hóa đơn sang
     * {@code COMPLETED}. Không publish event nào.
     *
     * <p>Ghi chú: §7 nói đặt thêm {@code dispenseId}, nhưng payload {@code prescription.filled}
     * không mang trường đó — đã chốt với pharmacy (2026-09-16) bỏ hẳn việc gán
     * {@code dispenseId}, xem {@code THELOC-INTEGRATION-FOLLOWUP.md}.
     */
    @Override
    @Transactional
    public void onPrescriptionFilled(PrescriptionFilledEvent e) {
        if (processedEvent.alreadyProcessed(e.eventId())) {
            return;
        }
        Optional<Invoice> found = invoiceRepo.findByPrescriptionForUpdate(e.prescriptionId());
        if (found.isEmpty()) {
            log.warn("Bỏ qua hoàn tất saga: không có hóa đơn cho prescriptionId={} (event {})",
                    e.prescriptionId(), e.eventId());
            processedEvent.markProcessed(e.eventId(), RK_PRESCRIPTION_FILLED);
            return;
        }

        Invoice invoice = found.get();
        invoice.transitionSaga(SagaStatus.COMPLETED);
        invoiceRepo.save(invoice);

        processedEvent.markProcessed(e.eventId(), RK_PRESCRIPTION_FILLED);
    }

    @Override
    @Transactional
    public void onPrescriptionCancelled(PrescriptionCancelledEvent e) {
        closeForPharmacyTermination(e.eventId(), e.prescriptionId(), e.correlationId(),
                e.reason(), RK_PRESCRIPTION_CANCELLED);
    }

    @Override
    @Transactional
    public void onPrescriptionExpired(PrescriptionExpiredEvent e) {
        closeForPharmacyTermination(e.eventId(), e.prescriptionId(), e.correlationId(),
                "PRESCRIPTION_EXPIRED", RK_PRESCRIPTION_EXPIRED);
    }

    /** Closes an unpaid invoice, or compensates it when payment won the race with termination. */
    private void closeForPharmacyTermination(UUID eventId, UUID prescriptionId,
                                               String correlationId, String reason,
                                               String routingKey) {
        if (processedEvent.alreadyProcessed(eventId)) {
            return;
        }
        Optional<Invoice> found = invoiceRepo.findByPrescriptionForUpdate(prescriptionId);
        if (found.isEmpty()) {
            log.warn("Bỏ qua kết thúc saga: không có hóa đơn cho prescriptionId={} (event {})",
                    prescriptionId, eventId);
            processedEvent.markProcessed(eventId, routingKey);
            return;
        }

        Invoice invoice = found.get();
        if (invoice.getSagaStatus() == SagaStatus.REFUNDED) {
            processedEvent.markProcessed(eventId, routingKey);
            return;
        }
        if (invoice.getSagaStatus() == SagaStatus.AWAITING_PAYMENT) {
            invoice.cancelBeforePayment();
            invoiceRepo.save(invoice);
        } else if (invoice.getSagaStatus() == SagaStatus.AWAITING_DISPENSE) {
            List<Fee> fees = feeRepo.findByInvoice(invoice.getInvoiceId());
            fees.forEach(Fee::refund);
            feeRepo.saveAll(fees);
            invoice.refund();
            invoiceRepo.save(invoice);
            eventPublisher.publishPaymentFailed(new PaymentFailedEvent(
                    UUID.randomUUID(), Instant.now(), correlationId,
                    invoice.getInvoiceId(), invoice.getPatientId(), normalizeReason(reason)));
        } else {
            throw new com.mediflow.billing.domain.exception.BillingRuleException(
                    "BILLING_INVALID_SAGA_TRANSITION",
                    "Không thể kết thúc invoice ở trạng thái " + invoice.getSagaStatus());
        }
        processedEvent.markProcessed(eventId, routingKey);
    }

    private static String normalizeReason(String reason) {
        return reason == null || reason.isBlank() ? "PRESCRIPTION_TERMINATED" : reason.trim();
    }
}
