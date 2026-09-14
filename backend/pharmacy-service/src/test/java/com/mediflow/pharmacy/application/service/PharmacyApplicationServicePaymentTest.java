package com.mediflow.pharmacy.application.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.mediflow.pharmacy.application.dto.command.PaymentCompletedCommand;
import com.mediflow.pharmacy.application.dto.response.DispenseDTO;
import com.mediflow.pharmacy.application.port.in.DispensePrescriptionUseCase;
import com.mediflow.pharmacy.application.port.out.PrescriptionRepositoryPort;
import com.mediflow.pharmacy.application.port.out.ProcessedEventPort;
import com.mediflow.pharmacy.application.port.out.PaymentReceiptClaimResult;
import com.mediflow.pharmacy.application.port.out.PaymentReceiptClaimStatus;
import com.mediflow.pharmacy.application.port.out.PaymentReceiptRepositoryPort;
import com.mediflow.pharmacy.domain.model.enums.DispenseStatus;
import com.mediflow.pharmacy.domain.exception.PaymentReceiptRuleException;
import com.mediflow.pharmacy.domain.exception.PrescriptionRuleException;
import com.mediflow.pharmacy.domain.model.PaymentReceipt;
import com.mediflow.pharmacy.domain.model.Prescription;
import com.mediflow.pharmacy.domain.model.PrescriptionLine;
import com.mediflow.pharmacy.domain.model.enums.PrescriptionStatus;

/**
 * Kiểm tra application flow khi nhận event thanh toán từ billing-service.
 */
class PaymentApplicationServiceTest {

    private static final UUID SYSTEM_USER = UUID.fromString("00000000-0000-0000-0000-000000000000");

    private final PrescriptionRepositoryPort prescriptionRepo = mock(PrescriptionRepositoryPort.class);
    private final ProcessedEventPort processedEventPort = mock(ProcessedEventPort.class);
    private final PaymentReceiptRepositoryPort paymentReceiptRepo = mock(PaymentReceiptRepositoryPort.class);
    private final DispensePrescriptionUseCase dispenseUseCase = mock(DispensePrescriptionUseCase.class);
    private final LatePaymentCompensationService latePaymentCompensationService = mock(LatePaymentCompensationService.class);

    private PaymentApplicationService service;

    /** Cô lập dispense và compensation qua in-port/service mock, giữ payment flow chạy thật. */
    @BeforeEach
    void setUp() {
        service = new PaymentApplicationService(
                prescriptionRepo, processedEventPort, paymentReceiptRepo, dispenseUseCase,
                latePaymentCompensationService);
    }

    /**
     * Hai lần giao cùng eventId chỉ được kích hoạt xuất thuốc một lần.
     */
    @Test
    void onPaymentCompleted_sameEventTwice_dispensesOnce() {
        UUID eventId = UUID.randomUUID();
        UUID prescriptionId = UUID.randomUUID();
        PaymentCompletedCommand command = command(eventId, prescriptionId);
        DispenseDTO result = new DispenseDTO(
                UUID.randomUUID(), prescriptionId, DispenseStatus.DISPENSED,
                Instant.parse("2026-08-31T03:01:00Z"), SYSTEM_USER, null);
        when(prescriptionRepo.findById(prescriptionId)).thenReturn(Optional.of(prescriptionFor(command)));
        PaymentReceipt receipt = receipt(command);
        when(paymentReceiptRepo.claim(org.mockito.ArgumentMatchers.any(PaymentReceipt.class)))
                .thenReturn(new PaymentReceiptClaimResult(PaymentReceiptClaimStatus.CLAIMED, receipt))
                .thenReturn(new PaymentReceiptClaimResult(PaymentReceiptClaimStatus.DUPLICATE_SAME, receipt));
        when(dispenseUseCase.dispenseWithPaymentProof(
                prescriptionId, SYSTEM_USER, command.invoiceId(), "payment-flow-001"))
                .thenReturn(result);

        service.onPaymentCompleted(command);
        service.onPaymentCompleted(command);

        verify(dispenseUseCase, times(1)).dispenseWithPaymentProof(
                prescriptionId, SYSTEM_USER, command.invoiceId(), "payment-flow-001");
        verify(processedEventPort, times(2)).claimIfAbsent(eventId, "payment.completed");
        verify(processedEventPort, org.mockito.Mockito.never()).markProcessed(eventId, "payment.completed");
    }

    /** Concurrent redelivery of one event is serialized and produces one dispense outcome. */
    @Test
    void onPaymentCompleted_sameEventConcurrent_dispensesOnce() throws Exception {
        UUID eventId = UUID.randomUUID();
        UUID prescriptionId = UUID.randomUUID();
        PaymentCompletedCommand command = command(eventId, prescriptionId);
        PaymentReceipt receipt = receipt(command);
        when(prescriptionRepo.findById(prescriptionId)).thenReturn(Optional.of(prescriptionFor(command)));
        when(paymentReceiptRepo.claim(org.mockito.ArgumentMatchers.any(PaymentReceipt.class)))
                .thenReturn(new PaymentReceiptClaimResult(PaymentReceiptClaimStatus.CLAIMED, receipt))
                .thenReturn(new PaymentReceiptClaimResult(PaymentReceiptClaimStatus.DUPLICATE_SAME, receipt));
        when(dispenseUseCase.dispenseWithPaymentProof(
                prescriptionId, SYSTEM_USER, command.invoiceId(), command.correlationId()))
                .thenReturn(new DispenseDTO(UUID.randomUUID(), prescriptionId, DispenseStatus.DISPENSED,
                        Instant.now(), SYSTEM_USER, command.correlationId()));
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        try {
            var first = executor.submit(() -> runConcurrent(command, ready));
            var second = executor.submit(() -> runConcurrent(command, ready));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            first.get(10, TimeUnit.SECONDS);
            second.get(10, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }
        verify(dispenseUseCase, times(1)).dispenseWithPaymentProof(
                prescriptionId, SYSTEM_USER, command.invoiceId(), command.correlationId());
    }

    private void runConcurrent(PaymentCompletedCommand command, CountDownLatch ready) {
        ready.countDown();
        service.onPaymentCompleted(command);
    }

    /** Lỗi dispense phải phát ra để broker retry và không ghi dấu processed riêng lần nữa. */
    @Test
    void onPaymentCompleted_dispenseFailure_propagatesForRetry() {
        UUID eventId = UUID.randomUUID();
        UUID prescriptionId = UUID.randomUUID();
        PaymentCompletedCommand command = command(eventId, prescriptionId);
        when(prescriptionRepo.findById(prescriptionId)).thenReturn(Optional.of(prescriptionFor(command)));
        when(paymentReceiptRepo.claim(org.mockito.ArgumentMatchers.any(PaymentReceipt.class)))
                .thenReturn(new PaymentReceiptClaimResult(
                        PaymentReceiptClaimStatus.CLAIMED, receipt(command)));
        doThrow(new IllegalStateException("temporary database outage")).when(dispenseUseCase).dispenseWithPaymentProof(
                prescriptionId, SYSTEM_USER, command.invoiceId(), "payment-flow-001");

        assertThatThrownBy(() -> service.onPaymentCompleted(command))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("temporary database outage");
        verify(processedEventPort, org.mockito.Mockito.never())
                .claimIfAbsent(eventId, "payment.completed");
        verify(processedEventPort, org.mockito.Mockito.never()).markProcessed(eventId, "payment.completed");
    }

    /** Event thanh toán lệch patient/department bị từ chối trước claim và không đụng tồn kho. */
    @Test
    void onPaymentCompleted_contextMismatch_rejectsBeforeClaim() {
        UUID eventId = UUID.randomUUID();
        UUID prescriptionId = UUID.randomUUID();
        PaymentCompletedCommand command = command(eventId, prescriptionId);
        Prescription mismatched = prescriptionFor(command);
        mismatched = Prescription.restore(prescriptionId, mismatched.getRecordId(), UUID.randomUUID(),
                mismatched.getDoctorId(), mismatched.getDepartmentId(), mismatched.getPrescribedDate(),
                mismatched.getTotalAmount(), mismatched.getLines(), PrescriptionStatus.ACTIVE,
                null, null, null, Instant.now(), Instant.now());
        when(prescriptionRepo.findById(prescriptionId)).thenReturn(Optional.of(mismatched));

        assertThatThrownBy(() -> service.onPaymentCompleted(command))
                .isInstanceOf(PrescriptionRuleException.class)
                .hasMessageContaining("không khớp");
        verify(processedEventPort, org.mockito.Mockito.never())
                .claimIfAbsent(eventId, "payment.completed");
        verify(dispenseUseCase, org.mockito.Mockito.never()).dispenseWithPaymentProof(
                org.mockito.ArgumentMatchers.eq(prescriptionId), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString());
    }

    /** Payload xung đột cùng eventId phải dừng workflow, không được chạm vào tồn kho. */
    @Test
    void onPaymentCompleted_receiptConflict_rejectsBeforeDispense() {
        UUID eventId = UUID.randomUUID();
        UUID prescriptionId = UUID.randomUUID();
        PaymentCompletedCommand command = command(eventId, prescriptionId);
        when(prescriptionRepo.findById(prescriptionId)).thenReturn(Optional.of(prescriptionFor(command)));
        when(paymentReceiptRepo.claim(org.mockito.ArgumentMatchers.any(PaymentReceipt.class)))
                .thenReturn(new PaymentReceiptClaimResult(
                        PaymentReceiptClaimStatus.DUPLICATE_CONFLICT, receipt(command)));

        assertThatThrownBy(() -> service.onPaymentCompleted(command))
                .isInstanceOf(PaymentReceiptRuleException.class)
                .hasMessageContaining("payload xung đột");
        verify(dispenseUseCase, org.mockito.Mockito.never()).dispenseWithPaymentProof(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    /** Receipt RECEIVED từ lần chạy trước phải được dùng để resume thay vì bỏ qua event. */
    @Test
    void onPaymentCompleted_receivedReceipt_resumesWorkflow() {
        UUID eventId = UUID.randomUUID();
        UUID prescriptionId = UUID.randomUUID();
        PaymentCompletedCommand command = command(eventId, prescriptionId);
        PaymentReceipt receipt = receipt(command);
        when(prescriptionRepo.findById(prescriptionId)).thenReturn(Optional.of(prescriptionFor(command)));
        when(paymentReceiptRepo.claim(org.mockito.ArgumentMatchers.any(PaymentReceipt.class)))
                .thenReturn(new PaymentReceiptClaimResult(PaymentReceiptClaimStatus.DUPLICATE_SAME, receipt));
        when(dispenseUseCase.dispenseWithPaymentProof(
                prescriptionId, SYSTEM_USER, command.invoiceId(), command.correlationId()))
                .thenReturn(new DispenseDTO(
                        UUID.randomUUID(), prescriptionId, DispenseStatus.DISPENSED,
                        Instant.now(), SYSTEM_USER, command.correlationId()));

        service.onPaymentCompleted(command);

        verify(dispenseUseCase).dispenseWithPaymentProof(
                prescriptionId, SYSTEM_USER, command.invoiceId(), command.correlationId());
        verify(paymentReceiptRepo).save(receipt);
        verify(processedEventPort).claimIfAbsent(eventId, "payment.completed");
    }

    /** Payment đến sau khi đơn đã hủy phải được claim và phát compensation, không retry vô hạn. */
    @Test
    void onPaymentCompleted_cancelledPrescription_publishesCompensation() {
        UUID eventId = UUID.randomUUID();
        UUID prescriptionId = UUID.randomUUID();
        PaymentCompletedCommand command = command(eventId, prescriptionId);
        Prescription cancelled = prescriptionFor(command);
        cancelled.cancel(UUID.randomUUID(), "Hủy trước thanh toán", Instant.now());
        when(prescriptionRepo.findById(prescriptionId)).thenReturn(Optional.of(cancelled));
        when(paymentReceiptRepo.claim(org.mockito.ArgumentMatchers.any(PaymentReceipt.class)))
                .thenReturn(new PaymentReceiptClaimResult(
                        PaymentReceiptClaimStatus.CLAIMED, receipt(command)));
        doThrow(new IllegalStateException("PRESCRIPTION_NOT_ACTIVE")).when(dispenseUseCase).dispenseWithPaymentProof(
                prescriptionId, SYSTEM_USER, command.invoiceId(), "payment-flow-001");

        service.onPaymentCompleted(command);

        verify(latePaymentCompensationService).compensate(command, cancelled);
    }

    private PaymentCompletedCommand command(UUID eventId, UUID prescriptionId) {
        return new PaymentCompletedCommand(
                eventId,
                Instant.parse("2026-08-31T03:00:00Z"),
                "payment-flow-001",
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                prescriptionId,
                new BigDecimal("125000.00"),
                "CASH"
        );
    }

    /** Tạo payment receipt hợp lệ tương ứng với command cho các test workflow. */
    private PaymentReceipt receipt(PaymentCompletedCommand command) {
        return PaymentReceipt.receive(
                command.eventId(), command.invoiceId(), command.prescriptionId(), command.patientId(),
                command.departmentId(), command.totalAmount(), command.paymentMethod(),
                command.occurredAt(), command.correlationId(), null);
    }

    /** Dựng đơn có patient/department khớp payment để test qua được context gate. */
    private Prescription prescriptionFor(PaymentCompletedCommand command) {
        Prescription created = Prescription.create(
                UUID.randomUUID(), command.patientId(), UUID.randomUUID(), command.departmentId(),
                LocalDate.now(), List.of(PrescriptionLine.create(
                        UUID.randomUUID(), 1, new BigDecimal("100.00"), "Ngày 1 lần")));
        return Prescription.restore(command.prescriptionId(), created.getRecordId(), created.getPatientId(),
                created.getDoctorId(), created.getDepartmentId(), created.getPrescribedDate(), created.getTotalAmount(),
                created.getLines(), PrescriptionStatus.ACTIVE, null, null, null, Instant.now(), Instant.now());
    }
}
