package com.mediflow.billing.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.mediflow.billing.application.event.PaymentFailedEvent;
import com.mediflow.billing.application.event.PrescriptionDispenseFailedEvent;
import com.mediflow.billing.application.event.PrescriptionFilledEvent;
import com.mediflow.billing.application.port.out.BillingEventPublisherPort;
import com.mediflow.billing.application.port.out.FeeRepositoryPort;
import com.mediflow.billing.application.port.out.InvoiceRepositoryPort;
import com.mediflow.billing.application.port.out.ProcessedEventPort;
import com.mediflow.billing.domain.model.Fee;
import com.mediflow.billing.domain.model.FeeType;
import com.mediflow.billing.domain.model.Invoice;
import com.mediflow.billing.domain.model.SagaStatus;

/** Hai nhánh kết thúc saga — BR-B4 (đảo thanh toán), BR-B5 (publish payment.failed), BR-B11 (COMPLETED). */
class SagaCompensationServiceTest {

    private final ProcessedEventPort processedEvent = mock(ProcessedEventPort.class);
    private final InvoiceRepositoryPort invoiceRepo = mock(InvoiceRepositoryPort.class);
    private final FeeRepositoryPort feeRepo = mock(FeeRepositoryPort.class);
    private final BillingEventPublisherPort publisher = mock(BillingEventPublisherPort.class);

    private final SagaCompensationService service = new SagaCompensationService(
            processedEvent, invoiceRepo, feeRepo, publisher);

    /** Hóa đơn saga đã trả tiền, đang chờ xuất thuốc. */
    private static Invoice paidAwaitingDispense(UUID prescriptionId) {
        return Invoice.restore(UUID.randomUUID(), UUID.randomUUID(), LocalDate.now(),
                new BigDecimal("300000.00"), true, com.mediflow.billing.domain.model.PaymentMethod.CASH,
                null, prescriptionId, SagaStatus.AWAITING_DISPENSE, Instant.now(), Instant.now(), null);
    }

    private static Fee paidFee() {
        Fee f = Fee.create(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                FeeType.DRUG, LocalDate.now(), new BigDecimal("300000.00"));
        f.markPaid();
        return f;
    }

    // ---- BR-B4 : xuất thuốc thất bại thì đảo thanh toán hóa đơn + các khoản phí ----
    @Test
    void onDispenseFailed_reversesInvoiceAndFees() {
        UUID prescriptionId = UUID.randomUUID();
        Invoice invoice = paidAwaitingDispense(prescriptionId);
        List<Fee> fees = List.of(paidFee(), paidFee());
        when(processedEvent.alreadyProcessed(any())).thenReturn(false);
        when(invoiceRepo.findByPrescriptionForUpdate(prescriptionId)).thenReturn(Optional.of(invoice));
        when(feeRepo.findByInvoice(invoice.getInvoiceId())).thenReturn(fees);
        when(feeRepo.saveAll(anyList())).thenAnswer(i -> i.getArgument(0));

        service.onDispenseFailed(new PrescriptionDispenseFailedEvent(
                UUID.randomUUID(), Instant.now(), "corr", prescriptionId, null, null, "Hết thuốc", List.of()));

        assertThat(invoice.isAlreadyPaid()).isFalse();
        assertThat(invoice.getSagaStatus()).isEqualTo(SagaStatus.REFUNDED);
        assertThat(fees).allSatisfy(f -> assertThat(f.isPaid()).isFalse());
        verify(invoiceRepo).save(invoice);
    }

    // ---- BR-B5 : nhánh bù trừ publish payment.failed ----
    @Test
    void onDispenseFailed_publishesPaymentFailed() {
        UUID prescriptionId = UUID.randomUUID();
        Invoice invoice = paidAwaitingDispense(prescriptionId);
        when(processedEvent.alreadyProcessed(any())).thenReturn(false);
        when(invoiceRepo.findByPrescriptionForUpdate(prescriptionId)).thenReturn(Optional.of(invoice));
        when(feeRepo.findByInvoice(any())).thenReturn(List.of());
        when(feeRepo.saveAll(anyList())).thenAnswer(i -> i.getArgument(0));

        service.onDispenseFailed(new PrescriptionDispenseFailedEvent(
                UUID.randomUUID(), Instant.now(), "corr", prescriptionId, null, null, "Kho lỗi", List.of()));

        ArgumentCaptor<PaymentFailedEvent> ev = ArgumentCaptor.forClass(PaymentFailedEvent.class);
        verify(publisher).publishPaymentFailed(ev.capture());
        assertThat(ev.getValue().invoiceId()).isEqualTo(invoice.getInvoiceId());
        assertThat(ev.getValue().patientId()).isEqualTo(invoice.getPatientId());
        assertThat(ev.getValue().reason()).isEqualTo("Kho lỗi");
    }

    @Test
    void onDispenseFailed_noInvoice_skipsQuietly() {
        UUID prescriptionId = UUID.randomUUID();
        when(processedEvent.alreadyProcessed(any())).thenReturn(false);
        when(invoiceRepo.findByPrescriptionForUpdate(prescriptionId)).thenReturn(Optional.empty());

        service.onDispenseFailed(new PrescriptionDispenseFailedEvent(
                UUID.randomUUID(), Instant.now(), "corr", prescriptionId, null, null, "x", List.of()));

        verify(invoiceRepo, never()).save(any());
        verify(publisher, never()).publishPaymentFailed(any());
        verify(processedEvent).markProcessed(any(), any());
    }

    // ---- BR-B11 : xuất thuốc thành công thì kết thúc saga ----
    @Test
    void onPrescriptionFilled_setsCompleted() {
        UUID prescriptionId = UUID.randomUUID();
        Invoice invoice = paidAwaitingDispense(prescriptionId);
        when(processedEvent.alreadyProcessed(any())).thenReturn(false);
        when(invoiceRepo.findByPrescriptionForUpdate(prescriptionId)).thenReturn(Optional.of(invoice));

        service.onPrescriptionFilled(new PrescriptionFilledEvent(
                UUID.randomUUID(), Instant.now(), "corr", prescriptionId, invoice.getPatientId(),
                UUID.randomUUID(), new BigDecimal("300000.00"), List.of()));

        assertThat(invoice.getSagaStatus()).isEqualTo(SagaStatus.COMPLETED);
        verify(invoiceRepo).save(invoice);
        verify(publisher, never()).publishPaymentCompleted(any());
        verify(publisher, never()).publishPaymentFailed(any());
    }

    @Test
    void onPrescriptionFilled_redelivered_isIdempotent() {
        UUID prescriptionId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        when(processedEvent.alreadyProcessed(eventId)).thenReturn(true);

        service.onPrescriptionFilled(new PrescriptionFilledEvent(
                eventId, Instant.now(), "corr", prescriptionId, UUID.randomUUID(),
                UUID.randomUUID(), new BigDecimal("1.00"), List.of()));

        verify(invoiceRepo, never()).findByPrescription(any());
        verify(invoiceRepo, never()).save(any());
    }
}
