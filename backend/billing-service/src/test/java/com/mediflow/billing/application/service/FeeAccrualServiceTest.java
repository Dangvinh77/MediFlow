package com.mediflow.billing.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.mediflow.billing.application.event.AppointmentStatusChangedEvent;
import com.mediflow.billing.application.event.LabResultCreatedEvent;
import com.mediflow.billing.application.event.MedicalRecordCreatedEvent;
import com.mediflow.billing.application.event.PrescriptionCreatedEvent;
import com.mediflow.billing.application.port.out.BillingEventPublisherPort;
import com.mediflow.billing.application.port.out.FeeRepositoryPort;
import com.mediflow.billing.application.port.out.InvoiceRepositoryPort;
import com.mediflow.billing.application.port.out.LabTestTypePort;
import com.mediflow.billing.application.port.out.PriceListPort;
import com.mediflow.billing.application.port.out.ProcessedEventPort;
import com.mediflow.billing.domain.model.Fee;
import com.mediflow.billing.domain.model.FeeType;
import com.mediflow.billing.domain.model.Invoice;

/** Sinh viện phí từ event — BR-B6 (một hóa đơn / đơn thuốc), BR-B7 (idempotent), BR-B8 (luôn có khoa). */
class FeeAccrualServiceTest {

    private final ProcessedEventPort processedEvent = mock(ProcessedEventPort.class);
    private final FeeRepositoryPort feeRepo = mock(FeeRepositoryPort.class);
    private final InvoiceRepositoryPort invoiceRepo = mock(InvoiceRepositoryPort.class);
    private final BillingEventPublisherPort publisher = mock(BillingEventPublisherPort.class);
    private final PriceListPort priceList = mock(PriceListPort.class);
    private final LabTestTypePort labTestTypePort = mock(LabTestTypePort.class);

    private final FeeAccrualService service = new FeeAccrualService(
            processedEvent, feeRepo, invoiceRepo, publisher, priceList, labTestTypePort);

    private static <T> T anyEvent(Class<T> t) {
        return any(t);
    }

    // ---- BR-B8 : mọi khoản phí sinh ra đều mang department_id (lấy từ event) ----
    @Test
    void accrueFee_alwaysSetsDepartmentId() {
        UUID recordId = UUID.randomUUID();
        UUID patientId = UUID.randomUUID();
        UUID dept = UUID.randomUUID();
        when(processedEvent.alreadyProcessed(any())).thenReturn(false);
        when(feeRepo.existsBySource(FeeType.EXAM, recordId)).thenReturn(false);
        when(priceList.examFee(dept)).thenReturn(new BigDecimal("150000.00"));
        when(feeRepo.save(any(Fee.class))).thenAnswer(i -> i.getArgument(0));

        service.onMedicalRecordCreated(new MedicalRecordCreatedEvent(
                UUID.randomUUID(), Instant.now(), "corr", recordId, patientId, dept, LocalDate.of(2026, 9, 5)));

        ArgumentCaptor<Fee> fee = ArgumentCaptor.forClass(Fee.class);
        verify(feeRepo).save(fee.capture());
        assertThat(fee.getValue().getDepartmentId()).isEqualTo(dept);
        assertThat(fee.getValue().getFeeType()).isEqualTo(FeeType.EXAM);
        assertThat(fee.getValue().getSourceRefId()).isEqualTo(recordId);
        assertThat(fee.getValue().getIncurredDate()).isEqualTo(LocalDate.of(2026, 9, 5));
        assertThat(fee.getValue().getAmount()).isEqualByComparingTo("150000.00");
    }

    // ---- BR-B7 : phí idempotent theo event nguồn (redelivery) ----
    @Test
    void onLabResult_sameEventTwice_createsOneFee() {
        UUID eventId = UUID.randomUUID();
        UUID labId = UUID.randomUUID();
        LabResultCreatedEvent e = new LabResultCreatedEvent(
                eventId, Instant.now(), "corr", labId, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        when(processedEvent.alreadyProcessed(eventId)).thenReturn(false, true);
        when(labTestTypePort.labType(labId)).thenReturn(Optional.of("CBC"));
        when(feeRepo.existsBySource(FeeType.LAB, labId)).thenReturn(false);
        when(priceList.labFee("CBC")).thenReturn(new BigDecimal("90000.00"));
        when(feeRepo.save(any(Fee.class))).thenAnswer(i -> i.getArgument(0));

        service.onLabResultCreated(e);
        service.onLabResultCreated(e);   // gửi lại

        verify(feeRepo, times(1)).save(any(Fee.class));
    }

    @Test
    void onLabResult_feeAlreadyExistsForSource_doesNotCreateDuplicate() {
        UUID labId = UUID.randomUUID();
        when(processedEvent.alreadyProcessed(any())).thenReturn(false);
        when(labTestTypePort.labType(labId)).thenReturn(Optional.of("CBC"));
        when(feeRepo.existsBySource(FeeType.LAB, labId)).thenReturn(true);   // đã có phí từ nguồn này

        service.onLabResultCreated(new LabResultCreatedEvent(
                UUID.randomUUID(), Instant.now(), "corr", labId, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()));

        verify(feeRepo, never()).save(any(Fee.class));
        verify(processedEvent).markProcessed(any(), eq("lab.result.created"));
    }

    @Test
    void onLabResult_noLabTypeKnown_skipsFeeButMarksProcessed() {
        UUID labId = UUID.randomUUID();
        when(processedEvent.alreadyProcessed(any())).thenReturn(false);
        when(labTestTypePort.labType(labId)).thenReturn(Optional.empty());

        service.onLabResultCreated(new LabResultCreatedEvent(
                UUID.randomUUID(), Instant.now(), "corr", labId, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()));

        verify(feeRepo, never()).save(any(Fee.class));
        verify(processedEvent).markProcessed(any(), eq("lab.result.created"));
    }

    // ---- BR-B6 : prescription.created chỉ tạo đúng một hóa đơn ----
    @Test
    void onPrescriptionCreated_twice_createsOneInvoice() {
        UUID prescriptionId = UUID.randomUUID();
        PrescriptionCreatedEvent e = new PrescriptionCreatedEvent(
                UUID.randomUUID(), Instant.now(), "corr", prescriptionId, UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("300000.00"), java.util.List.of());
        when(processedEvent.alreadyProcessed(any())).thenReturn(false);
        when(feeRepo.save(any(Fee.class))).thenAnswer(i -> i.getArgument(0));
        when(invoiceRepo.save(any(Invoice.class))).thenAnswer(i -> i.getArgument(0));
        // lần 1: chưa có hóa đơn; lần 2: đã có
        when(invoiceRepo.findByPrescription(prescriptionId))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(mock(Invoice.class)));

        service.onPrescriptionCreated(e);
        service.onPrescriptionCreated(e);

        verify(invoiceRepo, times(1)).save(any(Invoice.class));
        verify(publisher, times(1)).publishInvoiceCreated(anyEvent(com.mediflow.billing.application.event.InvoiceCreatedEvent.class));
    }

    @Test
    void onPrescriptionCreated_opensInvoiceAtAwaitingPaymentWithDrugFee() {
        UUID prescriptionId = UUID.randomUUID();
        UUID dept = UUID.randomUUID();
        PrescriptionCreatedEvent e = new PrescriptionCreatedEvent(
                UUID.randomUUID(), Instant.now(), "corr", prescriptionId, UUID.randomUUID(),
                UUID.randomUUID(), dept, new BigDecimal("300000.00"), java.util.List.of());
        when(processedEvent.alreadyProcessed(any())).thenReturn(false);
        when(invoiceRepo.findByPrescription(prescriptionId)).thenReturn(Optional.empty());
        when(feeRepo.save(any(Fee.class))).thenAnswer(i -> i.getArgument(0));
        when(invoiceRepo.save(any(Invoice.class))).thenAnswer(i -> i.getArgument(0));

        service.onPrescriptionCreated(e);

        ArgumentCaptor<Fee> fee = ArgumentCaptor.forClass(Fee.class);
        verify(feeRepo, org.mockito.Mockito.atLeastOnce()).save(fee.capture());
        assertThat(fee.getValue().getFeeType()).isEqualTo(FeeType.DRUG);
        assertThat(fee.getValue().getDepartmentId()).isEqualTo(dept);

        ArgumentCaptor<Invoice> invoice = ArgumentCaptor.forClass(Invoice.class);
        verify(invoiceRepo).save(invoice.capture());
        assertThat(invoice.getValue().getSagaStatus())
                .isEqualTo(com.mediflow.billing.domain.model.SagaStatus.AWAITING_PAYMENT);
        assertThat(invoice.getValue().getPrescriptionId()).isEqualTo(prescriptionId);
    }

    // ---- V1: appointment.status.changed ARRIVED không sinh phí (chống tính tiền 2 lần) ----
    @Test
    void onAppointmentStatusChanged_arrived_createsNoFeeButMarksProcessed() {
        when(processedEvent.alreadyProcessed(any())).thenReturn(false);

        service.onAppointmentStatusChanged(new AppointmentStatusChangedEvent(
                UUID.randomUUID(), Instant.now(), "corr", UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), "ARRIVED"));

        verify(feeRepo, never()).save(any(Fee.class));
        verify(processedEvent).markProcessed(any(), eq("appointment.status.changed"));
    }
}
