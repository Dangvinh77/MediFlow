package com.mediflow.pharmacy.application.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.mediflow.pharmacy.application.dto.response.DispenseDTO;
import com.mediflow.pharmacy.application.event.StockLowEvent;
import com.mediflow.pharmacy.application.event.carefinance.PrescriptionCareEvent.EventType;
import com.mediflow.pharmacy.application.mapper.DispenseDtoMapper;
import com.mediflow.pharmacy.application.mapper.PrescriptionCareEventFactory;
import com.mediflow.pharmacy.application.port.in.CapturePrescriptionCareEventUseCase;
import com.mediflow.pharmacy.application.port.in.DispenseCarePrescriptionUseCase;
import com.mediflow.pharmacy.application.port.in.RecordCareStockFailureUseCase;
import com.mediflow.pharmacy.application.port.out.DispenseSlipRepositoryPort;
import com.mediflow.pharmacy.application.port.out.DrugRepositoryPort;
import com.mediflow.pharmacy.application.port.out.PharmacyEventPublisherPort;
import com.mediflow.pharmacy.application.port.out.PrescriptionRepositoryPort;
import com.mediflow.pharmacy.application.port.out.PrescriptionCareEventWriterPort;
import com.mediflow.pharmacy.application.port.out.StockReservationRepositoryPort;
import com.mediflow.pharmacy.domain.exception.DispenseAuthorizationException;
import com.mediflow.pharmacy.domain.exception.DispenseNotFoundException;
import com.mediflow.pharmacy.domain.exception.DispenseRuleException;
import com.mediflow.pharmacy.domain.exception.DrugNotFoundException;
import com.mediflow.pharmacy.domain.exception.DrugRuleException;
import com.mediflow.pharmacy.domain.exception.PrescriptionNotFoundException;
import com.mediflow.pharmacy.domain.exception.StockReservationRuleException;
import com.mediflow.pharmacy.domain.model.Drug;
import com.mediflow.pharmacy.domain.model.DispenseActor;
import com.mediflow.pharmacy.domain.model.DispenseSlip;
import com.mediflow.pharmacy.domain.model.Prescription;
import com.mediflow.pharmacy.domain.model.PrescriptionLine;
import com.mediflow.pharmacy.domain.model.StockReservation;
import com.mediflow.pharmacy.domain.model.enums.CareContext;
import com.mediflow.pharmacy.domain.model.enums.CareContractVersion;
import com.mediflow.pharmacy.domain.model.enums.DispenseActorType;
import com.mediflow.pharmacy.domain.model.enums.DispenseStatus;
import com.mediflow.pharmacy.domain.model.enums.PrescriptionStatus;
import com.mediflow.pharmacy.domain.model.enums.ReservationReleaseReason;

/**
 * Internal, held-event V1 outpatient transaction. No public/legacy caller activates this path.
 * Authorization, stock, reservation, slip, prescription and held lifecycle bytes commit together.
 * Failures roll back; this class never calls V0 terminal failure/refund compensation.
 */
@Service
@Transactional
public class CareDispenseTransactionService implements DispenseCarePrescriptionUseCase, RecordCareStockFailureUseCase {
    private final PrescriptionRepositoryPort prescriptions;
    private final DispenseSlipRepositoryPort slips;
    private final DrugRepositoryPort drugs;
    private final StockReservationRepositoryPort reservations;
    private final PrescriptionClearanceAuthorizationService authorization;
    private final CapturePrescriptionCareEventUseCase capture;
    private final PrescriptionCareEventWriterPort writer;
    private final PharmacyEventPublisherPort events;
    private final DispenseDtoMapper mapper;
    private final Clock clock;

    public CareDispenseTransactionService(PrescriptionRepositoryPort prescriptions, DispenseSlipRepositoryPort slips,
            DrugRepositoryPort drugs, StockReservationRepositoryPort reservations,
            PrescriptionClearanceAuthorizationService authorization, CapturePrescriptionCareEventUseCase capture,
            PrescriptionCareEventWriterPort writer, PharmacyEventPublisherPort events, DispenseDtoMapper mapper, Clock clock) {
        this.prescriptions = prescriptions;
        this.slips = slips;
        this.drugs = drugs;
        this.reservations = reservations;
        this.authorization = authorization;
        this.capture = capture;
        this.writer = writer;
        this.events = events;
        this.mapper = mapper;
        this.clock = clock;
    }

    @Override
    public DispenseDTO execute(UUID prescriptionId, DispenseActor actor,
            String correlationId) {
        if (prescriptionId == null || correlationId == null || correlationId.isBlank()) {
            throw new IllegalArgumentException("Prescription and correlation identity are required");
        }
        if (actor == null || (actor.type() != DispenseActorType.STAFF && actor.type() != DispenseActorType.ACCOUNT)) {
            throw new DispenseAuthorizationException("PHARMACY_DISPENSE_ACTOR_REQUIRED",
                    "V1 dispensing requires an authenticated staff/account command, not automatic clearance dispensing");
        }
        var prescription = prescriptions.findByIdForUpdate(prescriptionId)
                .orElseThrow(() -> new PrescriptionNotFoundException("Prescription not found"));
        if (prescription.getCareContext().contractVersion() != CareContractVersion.V1
                || prescription.getCareContext().careContext() != CareContext.OUTPATIENT) {
            throw new DispenseAuthorizationException("PHARMACY_CARE_DISPENSE_CONTEXT_UNSUPPORTED",
                    "This internal transaction supports only V1 outpatient prescriptions");
        }
        var slip = slips.findByPrescriptionForUpdate(prescriptionId)
                .orElseThrow(() -> new DispenseNotFoundException("Dispense slip not found"));
        if (!prescriptionId.equals(slip.getPrescriptionId()) || slip.getDispenseId() == null) {
            throw new DispenseRuleException("PHARMACY_CARE_LIFECYCLE_EVIDENCE_INVALID", "Persisted slip identity differs");
        }
        if (slip.getStatus() == DispenseStatus.DISPENSED) {
            return readCompletedProof(prescription, slip);
        }
        if (!slip.isPending() || !prescription.isActive()) {
            throw new DispenseRuleException("DISPENSE_INVALID_TRANSITION", "Only an active prescription/pending slip can dispense");
        }
        List<PrescriptionLine> lines = prescription.getLines().stream()
                .sorted(java.util.Comparator.comparing(PrescriptionLine::getDrugId)).toList();
        var stock = lockStock(prescriptionId, lines);
        var grant = authorization.requireValidAfterLocks(prescription);
        // Verification may itself wait/write. Re-evaluate every temporal guard after that wait,
        // immediately before effects, instead of carrying an earlier stock/authorization clock.
        Instant now = clock.instant();
        if (!grant.isValidAt(now)) {
            throw new DispenseAuthorizationException("PHARMACY_CLEARANCE_REQUIRED", "Clearance expired before stock effects");
        }
        if (prescription.getCreatedAt() == null || now.isBefore(prescription.getCreatedAt())) {
            throw new DispenseRuleException("PHARMACY_CARE_LIFECYCLE_EVIDENCE_INVALID", "Invalid business timestamp");
        }
        var businessDate = now.atZone(clock.getZone()).toLocalDate();
        validateStock(stock, lines, now, businessDate);
        // All lines are validated first: no prefix of a multi-drug order can be dispensed alone.
        for (var line : lines) {
            var drug = stock.drugs().get(line.getDrugId());
            drug.dispenseStock(line.getQuantity(), businessDate);
            var reservation = stock.reservations().get(line.getDrugId());
            reservation.markFulfilled(now);
            reservations.save(reservation);
            drugs.save(drug);
        }
        slip.markDispensed(actor, now);
        var savedSlip = slips.save(slip);
        prescription.markFulfilled(now);
        prescriptions.save(prescription);
        capture.capture(prescriptionId, UUID.randomUUID(), EventType.FILLED, correlationId);
        for (var drug : stock.drugs().values()) {
            if (drug.belowLowStockThreshold()) {
                events.publishStockLow(new StockLowEvent(UUID.randomUUID(), now, correlationId, drug.getDrugId(),
                        drug.getDrugName(), drug.getStockQuantity(), drug.getLowStockThreshold()));
            }
        }
        return mapper.toDto(savedSlip);
    }

    private DispenseDTO readCompletedProof(Prescription prescription, DispenseSlip slip) {
        if (prescription.getStatus() != PrescriptionStatus.FULFILLED || prescription.getLifecycleAt() == null
                || !prescription.getLifecycleAt().equals(slip.getLifecycleAt())
                || !prescription.getLifecycleAt().equals(slip.getDispensedAt())) {
            throw new DispenseRuleException("PHARMACY_CARE_LIFECYCLE_EVIDENCE_INVALID", "Terminal business proof differs");
        }
        var stored = writer.findHeld(prescription.getPrescriptionId(), EventType.FILLED)
                .orElseThrow(() -> new DispenseRuleException("PHARMACY_CARE_LIFECYCLE_EVIDENCE_INVALID", "Held filled proof is missing"));
        var expectedProof = PrescriptionCareEventFactory.create(stored.eventId(), EventType.FILLED,
                stored.correlationId(), prescription, slip.getDispenseId(), null);
        if (!stored.equals(expectedProof)) {
            throw new DispenseRuleException("PHARMACY_CARE_LIFECYCLE_EVIDENCE_INVALID", "Held filled proof differs from persisted snapshots");
        }
        // Retries never mint an event ID, re-authorize an expired grant or decrement stock again.
        return mapper.toDto(slip);
    }

    /**
     * Only locked, current business stock evidence can end an order. No exception text, arbitrary
     * failure reason or V0 compensation is accepted. Invocation must follow rollback, not a catch
     * inside the failed transaction; MANDATORY capture/write still join this new business transaction.
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<DispenseDTO> recordStockFailure(UUID prescriptionId, DispenseActor actor, String correlationId) {
        if (prescriptionId == null || correlationId == null || correlationId.isBlank() || actor == null
                || (actor.type() != DispenseActorType.STAFF && actor.type() != DispenseActorType.ACCOUNT)) {
            throw new DispenseAuthorizationException("PHARMACY_DISPENSE_ACTOR_REQUIRED", "Authenticated stock-failure command is required");
        }
        var prescription = prescriptions.findByIdForUpdate(prescriptionId)
                .orElseThrow(() -> new PrescriptionNotFoundException("Prescription not found"));
        if (prescription.getCareContext().contractVersion() != CareContractVersion.V1
                || prescription.getCareContext().careContext() != CareContext.OUTPATIENT) {
            throw new DispenseAuthorizationException("PHARMACY_CARE_DISPENSE_CONTEXT_UNSUPPORTED", "Only V1 outpatient stock failure is supported");
        }
        var slip = slips.findByPrescriptionForUpdate(prescriptionId).orElseThrow(() -> new DispenseNotFoundException("Dispense slip not found"));
        if (slip.getDispenseId() == null || !prescriptionId.equals(slip.getPrescriptionId())) throw reservationInvalid();
        if (prescription.getStatus() == PrescriptionStatus.DISPENSE_FAILED) return Optional.of(readFailedProof(prescription, slip));
        if (!prescription.isActive()) return Optional.empty(); // Another terminal command won; never replace it.
        if (!slip.isPending()) throw reservationInvalid();
        var lines = prescription.getLines().stream().sorted(java.util.Comparator.comparing(PrescriptionLine::getDrugId)).toList();
        var stock = lockStock(prescriptionId, lines);
        if (stockFailureReason(stock, lines, clock.instant()) == null) return Optional.empty();
        var grant = authorization.requireValidAfterLocks(prescription);
        Instant now = clock.instant();
        if (!grant.isValidAt(now)) throw new DispenseAuthorizationException("PHARMACY_CLEARANCE_REQUIRED", "Clearance expired before failure effects");
        if (prescription.getCreatedAt() == null || now.isBefore(prescription.getCreatedAt())) throw reservationInvalid();
        String reason = stockFailureReason(stock, lines, now);
        if (reason == null) return Optional.empty();
        for (var reservation : stock.reservations().values()) {
            reservation.release(ReservationReleaseReason.DISPENSE_FAILED, actor.id(), now);
            reservations.save(reservation);
        }
        prescription.markDispenseFailed(now);
        slip.markFailed(reason, now);
        prescriptions.save(prescription);
        var savedSlip = slips.save(slip);
        capture.capture(prescriptionId, UUID.randomUUID(), EventType.DISPENSE_FAILED, correlationId);
        return Optional.of(mapper.toDto(savedSlip));
    }

    private String stockFailureReason(LockedStock stock, List<PrescriptionLine> lines, Instant now) {
        LocalDate date = now.atZone(clock.getZone()).toLocalDate();
        for (var line : lines) {
            if (stock.reservations().get(line.getDrugId()).isExpiredAt(now)) return "RESERVATION_EXPIRED";
            var drug = stock.drugs().get(line.getDrugId());
            if (!drug.hasStock(line.getQuantity())) return "DRUG_OUT_OF_STOCK";
            if (drug.isExpiredOn(date)) return "DRUG_EXPIRED";
        }
        return null;
    }

    private DispenseDTO readFailedProof(Prescription prescription, DispenseSlip slip) {
        if (slip.getStatus() != DispenseStatus.FAILED || prescription.getLifecycleAt() == null
                || !prescription.getLifecycleAt().equals(slip.getLifecycleAt())) throw reservationInvalid();
        var stored = writer.findHeld(prescription.getPrescriptionId(), EventType.DISPENSE_FAILED)
                .orElseThrow(() -> new DispenseRuleException("PHARMACY_CARE_LIFECYCLE_EVIDENCE_INVALID", "Held failed proof is missing"));
        if (!stored.equals(PrescriptionCareEventFactory.create(stored.eventId(), EventType.DISPENSE_FAILED,
                stored.correlationId(), prescription, null, slip.getFailureReason()))) throw reservationInvalid();
        return mapper.toDto(slip);
    }

    private LockedStock lockStock(UUID prescriptionId, List<PrescriptionLine> lines) {
        var expected = lines.stream().map(PrescriptionLine::getDrugId).collect(Collectors.toSet());
        var snapshot = reservations.findByPrescription(prescriptionId);
        if (lines.isEmpty() || expected.size() != lines.size() || snapshot.size() != lines.size()
                || !snapshot.stream().map(StockReservation::getDrugId).collect(Collectors.toSet()).equals(expected)) {
            throw reservationInvalid();
        }
        var lockedDrugs = new LinkedHashMap<UUID, Drug>();
        var lockedReservations = new LinkedHashMap<UUID, StockReservation>();
        for (var line : lines) {
            var drug = drugs.findByIdForUpdate(line.getDrugId())
                    .orElseThrow(() -> new DrugNotFoundException("Prescribed drug not found"));
            var reservation = reservations.findReservedByPrescriptionForUpdate(prescriptionId, line.getDrugId())
                    .orElseThrow(CareDispenseTransactionService::reservationInvalid);
            if (!line.getDrugId().equals(drug.getDrugId()) || !line.getDrugId().equals(reservation.getDrugId())
                    || !prescriptionId.equals(reservation.getPrescriptionId()) || !reservation.isReserved()
                    || reservation.getQuantity() != line.getQuantity()) {
                throw reservationInvalid();
            }
            lockedDrugs.put(line.getDrugId(), drug);
            lockedReservations.put(line.getDrugId(), reservation);
        }
        return new LockedStock(lockedDrugs, lockedReservations);
    }

    private static void validateStock(LockedStock stock, List<PrescriptionLine> lines, Instant now, LocalDate businessDate) {
        for (var line : lines) {
            if (stock.reservations().get(line.getDrugId()).isExpiredAt(now)) {
                throw new StockReservationRuleException("RESERVATION_EXPIRED", "Stock reservation expired before effects");
            }
            var drug = stock.drugs().get(line.getDrugId());
            if (!drug.hasStock(line.getQuantity())) throw new DrugRuleException("DRUG_OUT_OF_STOCK", "Insufficient stock");
            if (drug.isExpiredOn(businessDate)) throw new DrugRuleException("DRUG_EXPIRED", "Drug has expired");
        }
    }

    private record LockedStock(Map<UUID, Drug> drugs, Map<UUID, StockReservation> reservations) { }

    private static StockReservationRuleException reservationInvalid() {
        return new StockReservationRuleException("RESERVATION_MISMATCH", "Exact complete reserved stock is required");
    }
}
