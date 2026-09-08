package com.mediflow.billing.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.mediflow.billing.application.dto.request.CreateInvoiceRequest;
import com.mediflow.billing.application.dto.request.PayInvoiceRequest;
import com.mediflow.billing.application.dto.response.RevenueByDeptDTO;
import com.mediflow.billing.application.mapper.FeeDtoMapper;
import com.mediflow.billing.application.mapper.InvoiceDtoMapper;
import com.mediflow.billing.application.mapper.RevenueMapper;
import com.mediflow.billing.application.port.out.BillingEventPublisherPort;
import com.mediflow.billing.application.port.out.FeeRepositoryPort;
import com.mediflow.billing.application.port.out.InvoiceRepositoryPort;
import com.mediflow.billing.application.port.out.InvoiceRepositoryPort.DepartmentRevenue;
import com.mediflow.billing.domain.exception.InvoiceNotFoundException;
import com.mediflow.billing.domain.model.Fee;
import com.mediflow.billing.domain.model.FeeType;
import com.mediflow.billing.domain.model.Invoice;
import com.mediflow.billing.domain.model.PaymentMethod;
import com.mediflow.billing.domain.model.SagaStatus;

/** Orchestration của các use case do người dùng gọi — BR-B2, BR-B3, BR-B10 + đường lỗi 404. */
class BillingApplicationServiceTest {

    private final InvoiceRepositoryPort invoiceRepo = mock(InvoiceRepositoryPort.class);
    private final FeeRepositoryPort feeRepo = mock(FeeRepositoryPort.class);
    private final BillingEventPublisherPort publisher = mock(BillingEventPublisherPort.class);
    private final InvoiceDtoMapper invoiceDtoMapper = mock(InvoiceDtoMapper.class);
    private final FeeDtoMapper feeDtoMapper = mock(FeeDtoMapper.class);
    private final RevenueMapper revenueMapper = mock(RevenueMapper.class);

    private final BillingApplicationService service = new BillingApplicationService(
            invoiceRepo, feeRepo, publisher, invoiceDtoMapper, feeDtoMapper, revenueMapper);

    private static Fee unpaidFee(UUID departmentId, String amount) {
        return Fee.create(UUID.randomUUID(), UUID.randomUUID(), departmentId, UUID.randomUUID(),
                FeeType.SERVICE, LocalDate.now(), new BigDecimal(amount));
    }

    /** Mô phỏng persistence adapter: trả về hóa đơn đã có id (dùng restore để giữ nguyên các trường). */
    private static Invoice withId(Invoice in) {
        return Invoice.restore(UUID.randomUUID(), in.getPatientId(), in.getCreatedDate(), in.getTotalAmount(),
                in.isAlreadyPaid(), in.getPaymentMethod(), in.getDispenseId(), in.getPrescriptionId(),
                in.getSagaStatus(), in.getPaidAt(), java.time.Instant.now(), null);
    }

    // ---- BR-B2 : total_amount = Σ các khoản phí chưa trả ----
    @Test
    void createInvoice_sumsUnpaidFeesOnly() {
        UUID patientId = UUID.randomUUID();
        UUID dept = UUID.randomUUID();
        List<Fee> unpaid = List.of(unpaidFee(dept, "120000.00"), unpaidFee(dept, "80000.00"));
        when(feeRepo.findUnpaidByPatient(patientId)).thenReturn(unpaid);
        // adapter thật gán id khi lưu — mô phỏng lại để bước gắn phí vào hóa đơn có id thực
        when(invoiceRepo.save(any(Invoice.class))).thenAnswer(i -> withId(i.getArgument(0)));
        when(feeRepo.saveAll(anyList())).thenAnswer(i -> i.getArgument(0));

        service.create(new CreateInvoiceRequest(patientId, LocalDate.now()));

        ArgumentCaptor<Invoice> saved = ArgumentCaptor.forClass(Invoice.class);
        verify(invoiceRepo).save(saved.capture());
        assertThat(saved.getValue().getTotalAmount()).isEqualByComparingTo("200000.00");
        assertThat(saved.getValue().getSagaStatus()).isEqualTo(SagaStatus.NONE);
        // mọi khoản phí được gắn vào hóa đơn vừa lập
        assertThat(unpaid).allSatisfy(f -> assertThat(f.getInvoiceId()).isNotNull());
    }

    @Test
    void createInvoice_doesNotReassignFeesFromExistingInvoice() {
        UUID patient = UUID.randomUUID(), existingInvoice = UUID.randomUUID();
        Fee reserved = unpaidFee(UUID.randomUUID(), "300000");
        reserved.assignToInvoice(existingInvoice);
        when(feeRepo.findUnpaidByPatient(patient)).thenReturn(List.of(reserved));
        assertThatThrownBy(() -> service.create(new CreateInvoiceRequest(patient, LocalDate.now())))
                .isInstanceOf(com.mediflow.billing.domain.exception.BillingRuleException.class);
        assertThat(reserved.getInvoiceId()).isEqualTo(existingInvoice);
        org.mockito.Mockito.verifyNoInteractions(invoiceRepo, publisher);
    }

    // ---- BR-B3 : thanh toán đánh dấu mọi khoản phí liên quan ----
    @Test
    void pay_marksAllRelatedFeesPaid() {
        UUID invoiceId = UUID.randomUUID();
        Invoice invoice = Invoice.restore(invoiceId, UUID.randomUUID(), LocalDate.now(),
                new BigDecimal("200000.00"), false, null, null, null, SagaStatus.NONE, null,
                java.time.Instant.now(), null);
        List<Fee> fees = List.of(unpaidFee(UUID.randomUUID(), "120000.00"), unpaidFee(UUID.randomUUID(), "80000.00"));
        when(invoiceRepo.findById(invoiceId)).thenReturn(Optional.of(invoice));
        when(feeRepo.findByInvoice(invoiceId)).thenReturn(fees);
        when(invoiceRepo.save(any(Invoice.class))).thenAnswer(i -> i.getArgument(0));
        when(feeRepo.saveAll(anyList())).thenAnswer(i -> i.getArgument(0));

        service.pay(invoiceId, new PayInvoiceRequest(PaymentMethod.CASH));

        assertThat(invoice.isAlreadyPaid()).isTrue();
        ArgumentCaptor<List<Fee>> savedFees = ArgumentCaptor.forClass(List.class);
        verify(feeRepo).saveAll(savedFees.capture());
        assertThat(savedFees.getValue()).allSatisfy(f -> assertThat(f.isPaid()).isTrue());
    }

    @Test
    void pay_sagaInvoice_advancesToAwaitingDispenseAndPublishes() {
        UUID invoiceId = UUID.randomUUID();
        Invoice sagaInvoice = Invoice.restore(invoiceId, UUID.randomUUID(), LocalDate.now(),
                new BigDecimal("300000.00"), false, null, null, UUID.randomUUID(),
                SagaStatus.AWAITING_PAYMENT, null, java.time.Instant.now(), null);
        when(invoiceRepo.findById(invoiceId)).thenReturn(Optional.of(sagaInvoice));
        when(feeRepo.findByInvoice(invoiceId)).thenReturn(List.of());
        when(invoiceRepo.save(any(Invoice.class))).thenAnswer(i -> i.getArgument(0));
        when(feeRepo.saveAll(anyList())).thenAnswer(i -> i.getArgument(0));

        var result = service.pay(invoiceId, new PayInvoiceRequest(PaymentMethod.TRANSFER));

        assertThat(sagaInvoice.getSagaStatus()).isEqualTo(SagaStatus.AWAITING_DISPENSE);
        assertThat(result.sagaStatus()).isEqualTo(SagaStatus.AWAITING_DISPENSE);
        ArgumentCaptor<com.mediflow.billing.application.event.PaymentCompletedEvent> payment =
                ArgumentCaptor.forClass(com.mediflow.billing.application.event.PaymentCompletedEvent.class);
        verify(publisher).publishPaymentCompleted(payment.capture());
        assertThat(payment.getValue().correlationId()).isEqualTo(invoiceId.toString());
        assertThat(payment.getValue().prescriptionId()).isEqualTo(sagaInvoice.getPrescriptionId());
    }

    // ---- BR-B10 : doanh thu gom theo khoa + khoảng ngày ----
    @Test
    void revenue_groupsByDepartmentIdAndDateRange() {
        UUID dept = UUID.randomUUID();
        LocalDate from = LocalDate.of(2026, 9, 1);
        LocalDate to = LocalDate.of(2026, 9, 30);
        List<DepartmentRevenue> projection = List.of(new DepartmentRevenue(dept, new BigDecimal("500000.00"), 3));
        List<RevenueByDeptDTO> dtos = List.of(new RevenueByDeptDTO(dept, new BigDecimal("500000.00"), 3));
        when(invoiceRepo.sumRevenueByDepartment(dept, from, to)).thenReturn(projection);
        when(revenueMapper.toDtoList(projection)).thenReturn(dtos);

        assertThat(service.revenueByDepartment(dept, from, to)).isSameAs(dtos);
        verify(invoiceRepo).sumRevenueByDepartment(eq(dept), eq(from), eq(to));
    }

    @Test
    void getById_unknown_throwsNotFound() {
        UUID id = UUID.randomUUID();
        when(invoiceRepo.findById(id)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getById(id)).isInstanceOf(InvoiceNotFoundException.class);
    }

    @Test
    void pay_unknown_throwsNotFound() {
        UUID id = UUID.randomUUID();
        when(invoiceRepo.findById(id)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.pay(id, new PayInvoiceRequest(PaymentMethod.CASH)))
                .isInstanceOf(InvoiceNotFoundException.class);
    }
}
