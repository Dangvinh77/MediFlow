package com.mediflow.pharmacy.application.service;

import com.mediflow.common.exception.BusinessRuleException;
import com.mediflow.pharmacy.application.dto.command.ActorIdentity;
import com.mediflow.pharmacy.application.dto.response.DispenseDTO;
import com.mediflow.pharmacy.application.port.in.DispensePrescriptionUseCase;
import com.mediflow.pharmacy.application.port.out.PaymentReceiptRepositoryPort;
import com.mediflow.pharmacy.domain.exception.PaymentProofRequiredException;
import com.mediflow.pharmacy.domain.model.DispenseActor;
import com.mediflow.pharmacy.domain.model.PaymentReceipt;
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

    /** Dispenses manually after resolving the invoice from the durable payment proof. */
    @Override
    public DispenseDTO dispense(UUID prescriptionId, UUID dispensedBy) {
        return dispense(prescriptionId, DispenseActor.staff(dispensedBy), null, null);
    }

    /** Dispenses while preserving the request/event correlation id. */
    @Override
    public DispenseDTO dispense(UUID prescriptionId, UUID dispensedBy, String correlationId) {
        return dispense(prescriptionId, DispenseActor.staff(dispensedBy), null, correlationId);
    }

    /** Manual dispense with a typed identity captured from the verified JWT. */
    @Override
    public DispenseDTO dispense(UUID prescriptionId, DispenseActor actor, String correlationId) {
        return dispense(prescriptionId, actor, null, correlationId);
    }

    /** Resolves the verified web identity inside the application boundary. */
    @Override
    public DispenseDTO dispense(UUID prescriptionId, ActorIdentity actor, String correlationId) {
        return dispense(prescriptionId, actor.dispenseAuditActor(), null, correlationId);
    }

    /** Runs stock transaction first and records a business failure only after rollback. */
    private DispenseDTO dispense(
            UUID prescriptionId,
            DispenseActor actor,
            UUID invoiceId,
            String correlationId) {
        PaymentReceipt receipt = requirePaymentProof(prescriptionId);
        if (invoiceId != null && !invoiceId.equals(receipt.getInvoiceId())) {
            throw new PaymentProofRequiredException();
        }
        // The durable receipt is the source of truth for compensation context; never trust a
        // caller-supplied invoice id that could point at another billing aggregate.
        return execute(prescriptionId, actor, receipt.getInvoiceId(), correlationId);
    }

    /** Executes a payment-driven dispense while preserving its explicit system actor kind. */
    @Override
    public DispenseDTO dispenseWithPaymentProof(
            UUID prescriptionId,
            DispenseActor actor,
            UUID invoiceId,
            String correlationId) {
        return execute(prescriptionId, actor, invoiceId, correlationId);
    }

    private DispenseDTO execute(
            UUID prescriptionId,
            DispenseActor actor,
            UUID invoiceId,
            String correlationId) {
        String normalizedCorrelationId = correlationId == null || correlationId.isBlank()
                ? UUID.randomUUID().toString() : correlationId.trim();
        try {
            return dispenseTransactionService.execute(
                    prescriptionId, actor, normalizedCorrelationId);
        } catch (BusinessRuleException exception) {
            recordDispenseFailureService.record(
                    prescriptionId,
                    actor.id(),
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
