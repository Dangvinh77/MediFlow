package com.mediflow.pharmacy.application.service;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.mediflow.pharmacy.application.event.carefinance.PrescriptionCareEvent.EventType;
import com.mediflow.pharmacy.application.mapper.PrescriptionCareEventFactory;
import com.mediflow.pharmacy.application.port.in.CapturePrescriptionCareEventUseCase;
import com.mediflow.pharmacy.application.port.out.DispenseSlipRepositoryPort;
import com.mediflow.pharmacy.application.port.out.PrescriptionCareEventWriterPort;
import com.mediflow.pharmacy.application.port.out.PrescriptionRepositoryPort;
import com.mediflow.pharmacy.domain.exception.PrescriptionRuleException;
import com.mediflow.pharmacy.domain.model.enums.DispenseStatus;

/** Builds from locked, persisted business evidence. Only internal V1 lifecycle commands call this hook. */
@Service
@Transactional(propagation = Propagation.MANDATORY)
public class PrescriptionCareEventCaptureService implements CapturePrescriptionCareEventUseCase {
    private final PrescriptionRepositoryPort prescriptions;
    private final DispenseSlipRepositoryPort slips;
    private final PrescriptionCareEventWriterPort writer;

    public PrescriptionCareEventCaptureService(PrescriptionRepositoryPort prescriptions,
            DispenseSlipRepositoryPort slips, PrescriptionCareEventWriterPort writer) {
        this.prescriptions = prescriptions;
        this.slips = slips;
        this.writer = writer;
    }

    @Override
    public void capture(UUID prescriptionId, UUID eventId, EventType type, String correlationId) {
        if (prescriptionId == null || eventId == null || type == null || correlationId == null || correlationId.isBlank()) {
            throw new IllegalArgumentException("Complete lifecycle capture identity is required");
        }
        var prescription = prescriptions.findByIdForUpdate(prescriptionId)
                .orElseThrow(() -> invalid("Prescription not found"));
        UUID dispenseId = null;
        String reason = type == EventType.CANCELLED ? prescription.getCancellationReason() : null;
        if (type != EventType.CREATED) {
            var slip = slips.findByPrescriptionForUpdate(prescriptionId).orElseThrow(() -> invalid("Persisted terminal slip is required"));
            DispenseStatus expected = switch (type) {
                case FILLED -> DispenseStatus.DISPENSED;
                case CANCELLED -> DispenseStatus.CANCELLED;
                case EXPIRED -> DispenseStatus.EXPIRED;
                case DISPENSE_FAILED -> DispenseStatus.FAILED;
                default -> throw new IllegalArgumentException("Not a terminal event");
            };
            if (slip.getDispenseId() == null || !prescriptionId.equals(slip.getPrescriptionId()) || slip.getStatus() != expected) {
                throw invalid("Terminal slip identity/state differs from the prescription");
            }
            if (slip.getLifecycleAt() == null || !slip.getLifecycleAt().equals(prescription.getLifecycleAt())) {
                throw invalid("Terminal timestamp must match the persisted lifecycle proof");
            }
            if (type == EventType.FILLED) {
                if (!java.util.Objects.equals(slip.getDispensedAt(), prescription.getLifecycleAt())) {
                    throw invalid("Filled timestamp must match the persisted dispense proof");
                }
                dispenseId = slip.getDispenseId();
            } else if (type == EventType.EXPIRED || type == EventType.DISPENSE_FAILED) {
                reason = slip.getFailureReason();
            } else if (!java.util.Objects.equals(reason, slip.getFailureReason())) {
                throw invalid("Cancellation reason must match both persisted lifecycle snapshots");
            }
        }
        writer.storeHeld(PrescriptionCareEventFactory.create(eventId, type, correlationId, prescription, dispenseId, reason));
    }

    private static PrescriptionRuleException invalid(String message) {
        return new PrescriptionRuleException("PHARMACY_CARE_LIFECYCLE_EVIDENCE_INVALID", message);
    }
}
