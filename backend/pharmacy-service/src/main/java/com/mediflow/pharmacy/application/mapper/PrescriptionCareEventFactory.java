package com.mediflow.pharmacy.application.mapper;

import java.time.Instant;
import java.util.UUID;

import com.mediflow.pharmacy.application.event.carefinance.PrescriptionCareEvent;
import com.mediflow.pharmacy.application.event.carefinance.PrescriptionCareEvent.EventType;
import com.mediflow.pharmacy.domain.model.Prescription;
import com.mediflow.pharmacy.domain.model.PrescriptionLine;
import com.mediflow.pharmacy.domain.model.enums.CareContractVersion;
import com.mediflow.pharmacy.domain.model.enums.PrescriptionStatus;

/** Immutable producer snapshot only. Does not mutate a prescription, dispense, or resolve a live catalogue. */
public final class PrescriptionCareEventFactory {
    private PrescriptionCareEventFactory() { }

    /** P-02.5: generate only from immutable persisted context/items and explicit business evidence. */
    public static PrescriptionCareEvent create(UUID eventId, EventType type, String correlationId,
            Prescription prescription, UUID dispenseId, String reason) {
        if (prescription == null || type == null || prescription.getPrescriptionId() == null
                || prescription.getCareContext().contractVersion() != CareContractVersion.V1) {
            throw new IllegalArgumentException("A persisted V1 prescription is required");
        }
        PrescriptionStatus expected = switch (type) {
            case CREATED -> PrescriptionStatus.ACTIVE;
            case FILLED -> PrescriptionStatus.FULFILLED;
            case CANCELLED -> PrescriptionStatus.CANCELLED;
            case EXPIRED -> PrescriptionStatus.EXPIRED;
            case DISPENSE_FAILED -> PrescriptionStatus.DISPENSE_FAILED;
        };
        if (prescription.getStatus() != expected) throw new IllegalArgumentException("Event does not match persisted prescription state");
        if (type == EventType.CANCELLED && !java.util.Objects.equals(reason, prescription.getCancellationReason())) {
            throw new IllegalArgumentException("Cancellation reason must match its persisted snapshot");
        }
        Instant at = switch (type) {
            case CREATED -> prescription.getCreatedAt();
            default -> prescription.getLifecycleAt();
        };
        if (at == null || prescription.getCreatedAt() == null || at.isBefore(prescription.getCreatedAt())) {
            throw new IllegalArgumentException("Exact persisted lifecycle timestamp is required");
        }
        var care = prescription.getCareContext();
        // Hibernate reload order and creation-list order can differ; event bytes must not depend on it.
        var items = prescription.getLines().stream().sorted(java.util.Comparator.comparing(PrescriptionLine::getDrugId))
                .map(line -> new PrescriptionCareEvent.Item(line.getDrugId(),
                line.getDrugNameSnapshot(), line.getQuantity(), line.getUnitPrice())).toList();
        var payload = new PrescriptionCareEvent.Payload(prescription.getPrescriptionId(), prescription.getRecordId(),
                care.admissionId(), prescription.getPatientId(), prescription.getDepartmentId(), care.careContext(),
                care.episode().type(), care.episode().id(), "PRESCRIPTION", prescription.getPrescriptionId(), care.priceCode(),
                items, prescription.getTotalAmount(), dispenseId, type == EventType.CREATED ? at : null,
                type == EventType.FILLED ? at : null, type == EventType.DISPENSE_FAILED ? at : null,
                type == EventType.CANCELLED ? at : null, type == EventType.EXPIRED ? at : null, reason);
        return new PrescriptionCareEvent(eventId, type, 1, at, correlationId, "pharmacy-service", payload);
    }
}
