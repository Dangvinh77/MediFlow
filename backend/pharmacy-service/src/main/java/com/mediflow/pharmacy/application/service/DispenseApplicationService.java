package com.mediflow.pharmacy.application.service;

import com.mediflow.common.exception.BusinessRuleException;
import com.mediflow.pharmacy.application.dto.response.DispenseDTO;
import com.mediflow.pharmacy.application.port.in.DispensePrescriptionUseCase;
import com.mediflow.pharmacy.application.port.out.PaymentReceiptRepositoryPort;
import com.mediflow.pharmacy.domain.exception.PaymentProofRequiredException;
import com.mediflow.pharmacy.domain.model.enums.PaymentReceiptStatus;
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

    /** Dispenses manually without an invoice context. */
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
        requirePaymentProof(prescriptionId);
        return execute(prescriptionId, dispensedBy, invoiceId, correlationId);
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
        try {
            return dispenseTransactionService.execute(prescriptionId, dispensedBy, correlationId);
        } catch (BusinessRuleException exception) {
            recordDispenseFailureService.record(
                    prescriptionId,
                    dispensedBy,
                    invoiceId,
                    correlationId,
                    exception.getCode() + ": " + exception.getMessage());
            throw exception;
        }
    }

    private void requirePaymentProof(UUID prescriptionId) {
        boolean paid = paymentReceiptRepository.findByPrescriptionId(prescriptionId).stream()
                .anyMatch(receipt -> receipt.getStatus() == PaymentReceiptStatus.RECEIVED
                        || receipt.getStatus() == PaymentReceiptStatus.DISPENSED);
        if (!paid) {
            throw new PaymentProofRequiredException();
        }
    }
}
