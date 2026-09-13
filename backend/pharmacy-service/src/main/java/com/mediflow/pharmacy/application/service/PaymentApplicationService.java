package com.mediflow.pharmacy.application.service;

import com.mediflow.pharmacy.application.dto.command.PaymentCompletedCommand;
import com.mediflow.pharmacy.application.port.in.DispensePrescriptionUseCase;
import com.mediflow.pharmacy.application.port.in.ReactToPaymentUseCase;
import com.mediflow.pharmacy.application.port.out.ProcessedEventPort;
import com.mediflow.pharmacy.application.port.out.PrescriptionRepositoryPort;
import com.mediflow.pharmacy.domain.exception.PrescriptionNotFoundException;
import com.mediflow.pharmacy.domain.exception.PrescriptionRuleException;
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
    private final DispensePrescriptionUseCase dispenseUseCase;
    private final LatePaymentCompensationService latePaymentCompensationService;

    /** Creates the payment application service from ports and the shared dispense use case. */
    public PaymentApplicationService(
            PrescriptionRepositoryPort prescriptionRepository,
            ProcessedEventPort processedEventPort,
            DispensePrescriptionUseCase dispenseUseCase,
            LatePaymentCompensationService latePaymentCompensationService) {
        this.prescriptionRepository = prescriptionRepository;
        this.processedEventPort = processedEventPort;
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
        Prescription prescription = validatePaymentContext(command);
        if (processedEventPort.alreadyProcessed(command.eventId())) {
            return;
        }

        try {
            dispenseUseCase.dispense(
                    command.prescriptionId(),
                    SYSTEM_ACTOR,
                    command.invoiceId(),
                    command.correlationId());
            processedEventPort.claimIfAbsent(command.eventId(), PAYMENT_COMPLETED_ROUTING_KEY);
        } catch (RuntimeException exception) {
            Prescription current = prescriptionRepository.findById(command.prescriptionId()).orElse(null);
            if (current != null && (current.getStatus() == PrescriptionStatus.CANCELLED
                    || current.getStatus() == PrescriptionStatus.EXPIRED)) {
                latePaymentCompensationService.compensate(command, current);
                return;
            }
            if (current != null && current.getStatus() == PrescriptionStatus.DISPENSE_FAILED) {
                processedEventPort.claimIfAbsent(command.eventId(), PAYMENT_COMPLETED_ROUTING_KEY);
                return;
            }
            throw exception;
        }
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
