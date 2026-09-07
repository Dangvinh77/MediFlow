package com.mediflow.billing.application.service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mediflow.billing.application.dto.request.CreateInvoiceRequest;
import com.mediflow.billing.application.dto.request.PayInvoiceRequest;
import com.mediflow.billing.application.dto.response.InvoiceDTO;
import com.mediflow.billing.application.dto.response.PaymentResultDTO;
import com.mediflow.billing.application.dto.response.RevenueByDeptDTO;
import com.mediflow.billing.application.event.InvoiceCreatedEvent;
import com.mediflow.billing.application.event.PaymentCompletedEvent;
import com.mediflow.billing.application.mapper.FeeDtoMapper;
import com.mediflow.billing.application.mapper.InvoiceDtoMapper;
import com.mediflow.billing.application.mapper.RevenueMapper;
import com.mediflow.billing.application.port.in.ManageInvoiceUseCase;
import com.mediflow.billing.application.port.in.QueryRevenueUseCase;
import com.mediflow.billing.application.port.out.BillingEventPublisherPort;
import com.mediflow.billing.application.port.out.FeeRepositoryPort;
import com.mediflow.billing.application.port.out.InvoiceRepositoryPort;
import com.mediflow.billing.domain.exception.InvoiceNotFoundException;
import com.mediflow.billing.domain.model.Fee;
import com.mediflow.billing.domain.model.Invoice;
import com.mediflow.billing.domain.model.SagaStatus;
import com.mediflow.common.api.PageQuery;
import com.mediflow.common.api.PageResult;

/**
 * Application service của billing cho các use case do người dùng gọi qua HTTP: lập hóa đơn
 * thường, xem hóa đơn, thanh toán, và báo cáo doanh thu (backend-spec/06-billing.md §7, §8).
 *
 * <p>Điều phối: nhận lệnh từ in-port → hỏi out-port → gọi domain model → phát event. Không import
 * JPA/AMQP/HTTP; chỉ dùng {@code @Service}/{@code @Transactional} (được phép theo docs/ai/04).
 * Event được publish qua {@link BillingEventPublisherPort}; adapter (Phần 5/5) hoãn gửi tới sau
 * khi transaction commit.
 *
 * <p>Các nhánh sinh phí từ event và bù trừ saga nằm ở {@link FeeAccrualService} /
 * {@link SagaCompensationService}.
 */
@Service
public class BillingApplicationService implements ManageInvoiceUseCase, QueryRevenueUseCase {

    private final InvoiceRepositoryPort invoiceRepo;
    private final FeeRepositoryPort feeRepo;
    private final BillingEventPublisherPort eventPublisher;
    private final InvoiceDtoMapper invoiceDtoMapper;
    private final FeeDtoMapper feeDtoMapper;
    private final RevenueMapper revenueMapper;

    public BillingApplicationService(InvoiceRepositoryPort invoiceRepo, FeeRepositoryPort feeRepo,
                                     BillingEventPublisherPort eventPublisher, InvoiceDtoMapper invoiceDtoMapper,
                                     FeeDtoMapper feeDtoMapper, RevenueMapper revenueMapper) {
        this.invoiceRepo = invoiceRepo;
        this.feeRepo = feeRepo;
        this.eventPublisher = eventPublisher;
        this.invoiceDtoMapper = invoiceDtoMapper;
        this.feeDtoMapper = feeDtoMapper;
        this.revenueMapper = revenueMapper;
    }

    // ============================================================
    // ManageInvoiceUseCase
    // ============================================================

    /**
     * Lập hóa đơn thường: gộp mọi khoản phí chưa thanh toán của bệnh nhân. Tổng tiền do
     * {@code Invoice.create} tính từ danh sách phí (BR-B2); danh sách rỗng → {@code Invoice.create}
     * ném {@code BILLING_NO_UNPAID_FEES} (422, §11).
     */
    @Override
    @Transactional
    public InvoiceDTO create(CreateInvoiceRequest r) {
        List<Fee> unpaidFees = feeRepo.findUnpaidByPatient(r.patientId());
        Invoice invoice = Invoice.create(r.patientId(), r.createdDate(), unpaidFees);
        Invoice saved = invoiceRepo.save(invoice);

        unpaidFees.forEach(fee -> fee.assignToInvoice(saved.getInvoiceId()));
        List<Fee> savedFees = feeRepo.saveAll(unpaidFees);

        publishInvoiceCreated(saved, savedFees);
        return invoiceDtoMapper.toDto(saved, feeDtoMapper.toDtoList(savedFees));
    }

    @Override
    @Transactional(readOnly = true)
    public InvoiceDTO getById(UUID id) {
        Invoice invoice = invoiceRepo.findById(id)
                .orElseThrow(() -> new InvoiceNotFoundException("Không tìm thấy hóa đơn id=" + id));
        List<Fee> fees = feeRepo.findByInvoice(id);
        return invoiceDtoMapper.toDto(invoice, feeDtoMapper.toDtoList(fees));
    }

    /**
     * Danh sách hóa đơn của bệnh nhân. Bản danh sách <b>không</b> kèm chi tiết từng khoản phí
     * (tránh N+1 truy vấn); dùng {@link #getById(UUID)} để xem đầy đủ.
     */
    @Override
    @Transactional(readOnly = true)
    public PageResult<InvoiceDTO> byPatient(UUID patientId, PageQuery page) {
        return invoiceRepo.findByPatient(patientId, page)
                .map(invoice -> invoiceDtoMapper.toDto(invoice, List.of()));
    }

    /**
     * Thanh toán hóa đơn theo đúng thuật toán §7:
     * nạp hóa đơn → {@code invoice.pay} (BR-B1) → {@code markPaid} mọi khoản phí (BR-B3) →
     * nếu là hóa đơn từ đơn thuốc thì đẩy saga {@code AWAITING_PAYMENT → PAID → AWAITING_DISPENSE}
     * → lưu → publish {@code payment.completed} sau commit.
     */
    @Override
    @Transactional
    public PaymentResultDTO pay(UUID id, PayInvoiceRequest r) {
        Invoice invoice = invoiceRepo.findById(id)
                .orElseThrow(() -> new InvoiceNotFoundException("Không tìm thấy hóa đơn id=" + id));

        invoice.pay(r.paymentMethod(), Instant.now());

        List<Fee> fees = feeRepo.findByInvoice(id);
        fees.forEach(Fee::markPaid);
        feeRepo.saveAll(fees);

        if (invoice.getSagaStatus() == SagaStatus.AWAITING_PAYMENT) {
            invoice.transitionSaga(SagaStatus.PAID);
            invoice.transitionSaga(SagaStatus.AWAITING_DISPENSE);
        }

        Invoice saved = invoiceRepo.save(invoice);
        publishPaymentCompleted(saved, fees);

        return new PaymentResultDTO(saved.getInvoiceId(), true, saved.getTotalAmount(),
                saved.getPaymentMethod(), saved.getPaidAt(), saved.getSagaStatus());
    }

    // ============================================================
    // QueryRevenueUseCase
    // ============================================================

    @Override
    @Transactional(readOnly = true)
    public List<RevenueByDeptDTO> revenueByDepartment(UUID departmentId, LocalDate fromDate, LocalDate toDate) {
        return revenueMapper.toDtoList(invoiceRepo.sumRevenueByDepartment(departmentId, fromDate, toDate));
    }

    // ============================================================
    // Helpers
    // ============================================================

    private void publishInvoiceCreated(Invoice invoice, List<Fee> fees) {
        List<InvoiceCreatedEvent.Item> items = fees.stream()
                .map(fee -> new InvoiceCreatedEvent.Item(fee.getFeeId(), fee.getFeeType(), fee.getAmount()))
                .toList();
        // correlationId null: lối vào HTTP, không có trace id thượng nguồn (giống pharmacy-service).
        eventPublisher.publishInvoiceCreated(new InvoiceCreatedEvent(
                UUID.randomUUID(), Instant.now(), null,
                invoice.getInvoiceId(), invoice.getPatientId(), primaryDepartment(fees),
                invoice.getTotalAmount(), items));
    }

    private void publishPaymentCompleted(Invoice invoice, List<Fee> fees) {
        eventPublisher.publishPaymentCompleted(new PaymentCompletedEvent(
                UUID.randomUUID(), Instant.now(), null,
                invoice.getInvoiceId(), invoice.getPatientId(), primaryDepartment(fees),
                invoice.getPrescriptionId(), invoice.getTotalAmount(), invoice.getPaymentMethod()));
    }

    /** Khoa đại diện cho hóa đơn = khoa của khoản phí đầu tiên (report gom nhóm theo khoa). */
    private static UUID primaryDepartment(List<Fee> fees) {
        return fees.isEmpty() ? null : fees.get(0).getDepartmentId();
    }
}
