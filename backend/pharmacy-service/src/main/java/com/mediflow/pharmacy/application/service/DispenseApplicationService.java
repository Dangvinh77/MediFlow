package com.mediflow.pharmacy.application.service;

import com.mediflow.common.exception.BusinessRuleException;
import com.mediflow.pharmacy.application.dto.response.DispenseDTO;
import com.mediflow.pharmacy.application.port.in.DispensePrescriptionUseCase;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Thin application orchestrator for manual and payment-driven dispensing.
 *
 * <p>Stock mutation and failure persistence remain in separate transaction-specialist beans.
 * This class intentionally has no repository dependency and never catches infrastructure
 * failures, allowing the driving adapter to retry them.</p>
 */
@Service
public class DispenseApplicationService implements DispensePrescriptionUseCase {

    private final DispenseTransactionService dispenseTransactionService;
    private final RecordDispenseFailureService recordDispenseFailureService;

    /** Creates the dispense orchestrator. */
    public DispenseApplicationService(
            DispenseTransactionService dispenseTransactionService,
            RecordDispenseFailureService recordDispenseFailureService) {
        this.dispenseTransactionService = dispenseTransactionService;
        this.recordDispenseFailureService = recordDispenseFailureService;
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
}
