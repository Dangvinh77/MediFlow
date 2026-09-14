package com.mediflow.pharmacy.application.service;

import com.mediflow.common.exception.BusinessRuleException;
import com.mediflow.pharmacy.application.dto.command.PaymentCompletedCommand;
import com.mediflow.pharmacy.application.port.in.DispensePrescriptionUseCase;
import com.mediflow.pharmacy.application.port.in.ReactToPaymentUseCase;
import com.mediflow.pharmacy.application.port.out.ProcessedEventPort;
import com.mediflow.pharmacy.application.port.out.PaymentReceiptClaimResult;
import com.mediflow.pharmacy.application.port.out.PaymentReceiptRepositoryPort;
import com.mediflow.pharmacy.application.port.out.PrescriptionRepositoryPort;
import com.mediflow.pharmacy.domain.exception.PrescriptionNotFoundException;
import com.mediflow.pharmacy.domain.exception.PaymentReceiptRuleException;
import com.mediflow.pharmacy.domain.exception.PrescriptionRuleException;
import com.mediflow.pharmacy.domain.model.PaymentReceipt;
import com.mediflow.pharmacy.domain.model.Prescription;
import com.mediflow.pharmacy.domain.model.enums.PrescriptionStatus;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Application service for the inbound `payment.completed` use case.
 *
 * <p>It validates payment context, delegates all stock behavior to the dispense in-port and
 * claims the event only after a terminal business outcome. Infrastructure failures therefore
 * remain retryable by RabbitMQ.</p>
 */
@Service
public class PaymentApplicationService implements ReactToPaymentUseCase {

    private static final String PAYMENT_COMPLETED_ROUTING_KEY = "payment.completed";
    private static final UUID SYSTEM_ACTOR = UUID.fromString("00000000-0000-0000-0000-000000000000");

    private final PrescriptionRepositoryPort prescriptionRepository;
    private final ProcessedEventPort processedEventPort;
    private final PaymentReceiptRepositoryPort paymentReceiptRepository;
    private final DispensePrescriptionUseCase dispenseUseCase;
    private final LatePaymentCompensationService latePaymentCompensationService;

    /** Creates the payment application service from ports and the shared dispense use case. */
    public PaymentApplicationService(
            PrescriptionRepositoryPort prescriptionRepository,
            ProcessedEventPort processedEventPort,
            PaymentReceiptRepositoryPort paymentReceiptRepository,
            DispensePrescriptionUseCase dispenseUseCase,
            LatePaymentCompensationService latePaymentCompensationService) {
        this.prescriptionRepository = prescriptionRepository;
        this.processedEventPort = processedEventPort;
        this.paymentReceiptRepository = paymentReceiptRepository;
        this.dispenseUseCase = dispenseUseCase;
        this.latePaymentCompensationService = latePaymentCompensationService;
    }

    /**
     * Processes one payment event with idempotent terminal claiming.
     *
     * @param command validated payment event command
     */
    @Override
    public void onPaymentCompleted(PaymentCompletedCommand command) {
        // Atomic payment-receipt claim and pessimistic dispense locks provide coordination both
        // within one JVM and across replicas; no unbounded per-event lock map is needed.
        processPaymentCompleted(command);
    }

    /** Executes the payment workflow while the per-event coordination lock is held. */
    private void processPaymentCompleted(PaymentCompletedCommand command) {
        validatePaymentContext(command);
        PaymentReceiptClaimResult claim = paymentReceiptRepository.claim(PaymentReceipt.receive(
                command.eventId(),
                command.invoiceId(),
                command.prescriptionId(),
                command.patientId(),
                command.departmentId(),
                command.totalAmount(),
                command.paymentMethod(),
                command.occurredAt(),
                command.correlationId(),
                null));
        if (claim.conflict()) {
            throw new PaymentReceiptRuleException(
                    "PAYMENT_RECEIPT_PAYLOAD_CONFLICT",
                    "Payment event trùng eventId nhưng payload xung đột");
        }
        PaymentReceipt receipt = claim.receipt();
        if (receipt.isTerminal()) {
            processedEventPort.claimIfAbsent(command.eventId(), PAYMENT_COMPLETED_ROUTING_KEY);
            return;
        }

        try {
            dispenseUseCase.dispenseWithPaymentProof(
                    command.prescriptionId(),
                    SYSTEM_ACTOR,
                    command.invoiceId(),
                    command.correlationId());
            receipt.markDispensed();
            paymentReceiptRepository.save(receipt);
            processedEventPort.claimIfAbsent(command.eventId(), PAYMENT_COMPLETED_ROUTING_KEY);
        } catch (RuntimeException exception) {
            Prescription current = prescriptionRepository.findById(command.prescriptionId()).orElse(null);
            if (current != null && (current.getStatus() == PrescriptionStatus.CANCELLED
                    || current.getStatus() == PrescriptionStatus.EXPIRED)) {
                latePaymentCompensationService.compensate(command, current);
                completeCompensation(receipt, "PAYMENT_AFTER_TERMINAL_STATE");
                return;
            }
            if (current != null && current.getStatus() == PrescriptionStatus.DISPENSE_FAILED) {
                String failureCode = exception instanceof BusinessRuleException businessRule
                        ? businessRule.getCode()
                        : "DISPENSE_FAILED";
                completeCompensation(receipt, failureCode);
                return;
            }
            throw exception;
        }
    }

    /**
     * Finalizes a payment receipt only after the failure transaction has committed.
     *
     * <p>The receipt remains {@code RECEIVED} when this method itself fails, so RabbitMQ can
     * redeliver the event and resume the workflow instead of acknowledging an incomplete saga.</p>
     *
     * @param receipt claimed payment receipt
     * @param failureCode stable business failure code
     */
    private void completeCompensation(PaymentReceipt receipt, String failureCode) {
        receipt.markCompensated(failureCode);
        paymentReceiptRepository.save(receipt);
        processedEventPort.claimIfAbsent(receipt.getEventId(), PAYMENT_COMPLETED_ROUTING_KEY);
    }

    private Prescription validatePaymentContext(PaymentCompletedCommand command) {
        Prescription prescription = prescriptionRepository.findById(command.prescriptionId())
                .orElseThrow(() -> new PrescriptionNotFoundException(
                        "Không tìm thấy đơn thuốc id=" + command.prescriptionId()));
        if (!command.patientId().equals(prescription.getPatientId())
                || !command.departmentId().equals(prescription.getDepartmentId())) {
            throw new PrescriptionRuleException(
                    "PAYMENT_CONTEXT_MISMATCH",
                    "Payment event không khớp bệnh nhân hoặc khoa của đơn thuốc");
        }
        return prescription;
    }
}
