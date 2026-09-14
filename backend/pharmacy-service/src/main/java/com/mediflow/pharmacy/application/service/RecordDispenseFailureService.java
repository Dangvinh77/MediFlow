package com.mediflow.pharmacy.application.service;

import com.mediflow.pharmacy.application.event.PrescriptionDispenseFailedEvent;
import com.mediflow.pharmacy.application.port.out.DispenseSlipRepositoryPort;
import com.mediflow.pharmacy.application.port.out.DrugRepositoryPort;
import com.mediflow.pharmacy.application.port.out.PharmacyEventPublisherPort;
import com.mediflow.pharmacy.application.port.out.PrescriptionRepositoryPort;
import com.mediflow.pharmacy.application.port.out.StockReservationRepositoryPort;
import com.mediflow.pharmacy.domain.model.DispenseSlip;
import com.mediflow.pharmacy.domain.model.Prescription;
import com.mediflow.pharmacy.domain.model.StockReservation;
import com.mediflow.pharmacy.domain.model.PrescriptionLine;
import com.mediflow.pharmacy.domain.model.enums.ReservationReleaseReason;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persists the terminal dispense failure after the stock transaction has rolled back.
 *
 * <p>The separate Spring bean is intentional: {@code REQUIRES_NEW} is effective only when the
 * call crosses a proxy. The caller must invoke this service after the dispense executor returned,
 * not from inside the transaction that owns the stock locks.</p>
 */
@Service
public class RecordDispenseFailureService {

    private static final int MAX_REASON_LENGTH = 500;

    private final PrescriptionRepositoryPort prescriptionRepo;
    private final DispenseSlipRepositoryPort dispenseSlipRepo;
    private final StockReservationRepositoryPort reservationRepo;
    private final DrugRepositoryPort drugRepo;
    private final PharmacyEventPublisherPort eventPublisher;
    private final Clock clock;

    /**
     * Creates the compensation writer.
     *
     * @param prescriptionRepo prescription persistence port
     * @param dispenseSlipRepo dispense slip persistence port
     * @param reservationRepo reservation persistence port
     * @param drugRepo drug lookup port
     * @param eventPublisher compensation event port
     * @param clock business clock
     */
    public RecordDispenseFailureService(
            PrescriptionRepositoryPort prescriptionRepo,
            DispenseSlipRepositoryPort dispenseSlipRepo,
            StockReservationRepositoryPort reservationRepo,
            DrugRepositoryPort drugRepo,
            PharmacyEventPublisherPort eventPublisher,
            Clock clock) {
        this.prescriptionRepo = prescriptionRepo;
        this.dispenseSlipRepo = dispenseSlipRepo;
        this.reservationRepo = reservationRepo;
        this.drugRepo = drugRepo;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    /**
     * Writes FAILED state, releases remaining reservations and emits one compensation event.
     *
     * @param prescriptionId prescription that failed
     * @param dispensedBy actor or system identity
     * @param invoiceId invoice to compensate
     * @param correlationId saga correlation id
     * @param reason internal failure reason, safely truncated before persistence
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(
            UUID prescriptionId,
            UUID dispensedBy,
            UUID invoiceId,
            String correlationId,
            String reason) {
        Prescription prescription = prescriptionRepo.findByIdForUpdate(prescriptionId).orElse(null);
        if (prescription == null) {
            return;
        }
        DispenseSlip slip = dispenseSlipRepo.findByPrescriptionForUpdate(prescriptionId).orElse(null);
        if (slip == null || !slip.isPending() || !prescription.isActive()) {
            return;
        }

        Instant failedAt = Instant.now(clock);
        List<StockReservation> reservations = reservationRepo.findByPrescriptionForUpdate(prescriptionId);
        for (StockReservation reservation : reservations) {
            if (reservation.isReserved()) {
                reservation.release(ReservationReleaseReason.DISPENSE_FAILED, dispensedBy, failedAt);
                reservationRepo.save(reservation);
            }
        }
        prescription.markDispenseFailed(failedAt);
        prescriptionRepo.save(prescription);
        String safeReason = normalizeReason(reason);
        slip.markFailed(safeReason, failedAt);
        dispenseSlipRepo.save(slip);
        eventPublisher.publishPrescriptionDispenseFailed(new PrescriptionDispenseFailedEvent(
                UUID.randomUUID(), failedAt, correlationId, prescriptionId, invoiceId,
                prescription.getPatientId(), safeReason, failedItems(prescription)));
    }

    private String normalizeReason(String reason) {
        if (reason == null || reason.isBlank()) {
            return "Xuất thuốc thất bại";
        }
        String trimmed = reason.trim();
        return trimmed.length() <= MAX_REASON_LENGTH
                ? trimmed
                : trimmed.substring(0, MAX_REASON_LENGTH);
    }

    private List<PrescriptionDispenseFailedEvent.FailedItem> failedItems(Prescription prescription) {
        // Include a deterministic item snapshot for every active-prescription failure. Billing
        // and notification can then explain the exact order even for expiry or data-integrity
        // failures, instead of receiving an empty array.
        return prescription.getLines().stream()
                .map(PrescriptionLine::getDrugId)
                .distinct()
                .map(drugId -> drugRepo.findById(drugId).map(drug -> {
                    int reserved = reservationRepo.findReservedByDrug(drugId).stream()
                            .mapToInt(StockReservation::getQuantity)
                            .sum();
                    return new PrescriptionDispenseFailedEvent.FailedItem(
                            drugId, drug.getDrugName(), quantityFor(prescription, drugId),
                            Math.max(0, drug.getStockQuantity() - reserved));
                }).orElse(null))
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    private int quantityFor(Prescription prescription, UUID drugId) {
        return prescription.getLines().stream()
                .filter(line -> line.getDrugId().equals(drugId))
                .mapToInt(PrescriptionLine::getQuantity)
                .sum();
    }
}
