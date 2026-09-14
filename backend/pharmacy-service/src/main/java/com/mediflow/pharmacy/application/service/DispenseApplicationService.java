package com.mediflow.pharmacy.application.service;

import com.mediflow.common.exception.BusinessRuleException;
import com.mediflow.pharmacy.application.dto.response.DispenseDTO;
import com.mediflow.pharmacy.application.port.in.DispensePrescriptionUseCase;
import com.mediflow.pharmacy.application.port.out.PaymentReceiptRepositoryPort;
import com.mediflow.pharmacy.domain.exception.PaymentProofRequiredException;
import com.mediflow.pharmacy.domain.model.enums.PaymentReceiptStatus;
import com.mediflow.pharmacy.domain.model.PaymentReceipt;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Thin application orchestrator for manual and payment-driven dispensing.
 *
 * <p>Stock mutation and failure persistence remain in separate transaction-specialist beans.
 * Payment proof is checked here, before the shared transaction mutates stock.</p>
 */
@Service
public class DispenseApplicationService implements DispensePrescriptionUseCase {

    private final DispenseTransactionService dispenseTransactionService;
    private final RecordDispenseFailureService recordDispenseFailureService;
    private final PaymentReceiptRepositoryPort paymentReceiptRepository;

    /** Creates the dispense orchestrator. */
    public DispenseApplicationService(
            DispenseTransactionService dispenseTransactionService,
            RecordDispenseFailureService recordDispenseFailureService,
            PaymentReceiptRepositoryPort paymentReceiptRepository) {
        this.dispenseTransactionService = dispenseTransactionService;
        this.recordDispenseFailureService = recordDispenseFailureService;
        this.paymentReceiptRepository = paymentReceiptRepository;
    }

    /** Dispenses manually after resolving the invoice from the durable payment proof. */
    @Override
    public DispenseDTO dispense(UUID prescriptionId, UUID dispensedBy) {
        return dispense(prescriptionId, dispensedBy, null, null);
    }

    /** Dispenses while preserving the request/event correlation id. */
    @Override
    public DispenseDTO dispense(UUID prescriptionId, UUID dispensedBy, String correlationId) {
        return dispense(prescriptionId, dispensedBy, null, correlationId);
    }

    /**
     * Runs stock transaction first and records a business failure only after it has rolled back.
     */
    @Override
    public DispenseDTO dispense(
            UUID prescriptionId,
            UUID dispensedBy,
            UUID invoiceId,
        String correlationId) {
        PaymentReceipt receipt = requirePaymentProof(prescriptionId);
        if (invoiceId != null && !invoiceId.equals(receipt.getInvoiceId())) {
            throw new PaymentProofRequiredException();
        }
        // The durable receipt is the source of truth for compensation context; never trust a
        // caller-supplied invoice id that could point at another billing aggregate.
        return execute(prescriptionId, dispensedBy, receipt.getInvoiceId(), correlationId);
    }

    /** Executes the shared stock transaction for a payment event already claimed by pharmacy. */
    @Override
    public DispenseDTO dispenseWithPaymentProof(
            UUID prescriptionId,
            UUID dispensedBy,
            UUID invoiceId,
            String correlationId) {
        return execute(prescriptionId, dispensedBy, invoiceId, correlationId);
    }

    private DispenseDTO execute(
            UUID prescriptionId,
            UUID dispensedBy,
            UUID invoiceId,
            String correlationId) {
        String normalizedCorrelationId = correlationId == null || correlationId.isBlank()
                ? UUID.randomUUID().toString() : correlationId.trim();
        try {
            return dispenseTransactionService.execute(
                    prescriptionId, dispensedBy, normalizedCorrelationId);
        } catch (BusinessRuleException exception) {
            recordDispenseFailureService.record(
                    prescriptionId,
                    dispensedBy,
                    invoiceId,
                    normalizedCorrelationId,
                    exception.getCode() + ": " + exception.getMessage());
            throw exception;
        }
    }

    private PaymentReceipt requirePaymentProof(UUID prescriptionId) {
        return paymentReceiptRepository.findByPrescriptionId(prescriptionId).stream()
                .filter(receipt -> receipt.getStatus() == PaymentReceiptStatus.RECEIVED
                        || receipt.getStatus() == PaymentReceiptStatus.DISPENSED)
                .findFirst()
                .orElseThrow(PaymentProofRequiredException::new);
    }
}
